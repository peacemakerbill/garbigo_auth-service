package com.garbigo.auth.service;

import com.garbigo.auth.exception.CustomException;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.client.http.ByteArrayContent;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.model.File;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.UserCredentials;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.List;

@Service
public class GoogleDriveStorageService {

    private static final Logger log = LoggerFactory.getLogger(GoogleDriveStorageService.class);
    private static final String FOLDER_MIME = "application/vnd.google-apps.folder";

    public record StoredFile(String fileId, String name, long sizeBytes) {
    }

    @Value("${google.drive.client-id:}")
    private String clientId;

    @Value("${google.drive.client-secret:}")
    private String clientSecret;

    @Value("${google.drive.refresh-token:}")
    private String refreshToken;

    @Value("${google.drive.root-folder-id:}")
    private String rootFolderId;

    @Value("${google.drive.root-folder-name:Garbigo Collector Applications}")
    private String rootFolderName;

    private volatile Drive drive;
    private volatile String resolvedRootFolderId;

    public boolean isConfigured() {
        return notBlank(clientId) && notBlank(clientSecret) && notBlank(refreshToken);
    }

    public String rootFolderId() {
        if (notBlank(rootFolderId)) {
            return rootFolderId.trim();
        }
        String local = resolvedRootFolderId;
        if (local == null) {
            synchronized (this) {
                local = resolvedRootFolderId;
                if (local == null) {
                    local = ensureFolder(rootFolderName.trim(), "root");
                    resolvedRootFolderId = local;
                }
            }
        }
        return local;
    }

    public String ensureFolder(String name, String parentId) {
        try {
            String query = "mimeType='" + FOLDER_MIME + "' and name='" + escape(name) + "' and '"
                    + escape(parentId) + "' in parents and trashed=false";
            List<File> found = client().files().list()
                    .setQ(query)
                    .setFields("files(id,name)")
                    .setPageSize(1)
                    .execute()
                    .getFiles();
            if (found != null && !found.isEmpty()) {
                return found.get(0).getId();
            }
            File metadata = new File();
            metadata.setName(name);
            metadata.setMimeType(FOLDER_MIME);
            metadata.setParents(List.of(parentId));
            return client().files().create(metadata).setFields("id").execute().getId();
        } catch (IOException e) {
            log.error("DRIVE: could not create or find folder {}", name, e);
            throw unavailable();
        }
    }

    public StoredFile upload(String folderId, String storedName, String mimeType, byte[] data, String description) {
        try {
            File metadata = new File();
            metadata.setName(storedName);
            metadata.setParents(List.of(folderId));
            metadata.setDescription(description);
            File created = client().files()
                    .create(metadata, new ByteArrayContent(mimeType, data))
                    .setFields("id,name,size")
                    .execute();
            return new StoredFile(created.getId(), created.getName(),
                    created.getSize() == null ? data.length : created.getSize());
        } catch (IOException e) {
            log.error("DRIVE: upload failed for {}", storedName, e);
            throw unavailable();
        }
    }

    public byte[] download(String fileId) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            client().files().get(fileId).executeMediaAndDownloadTo(out);
            return out.toByteArray();
        } catch (GoogleJsonResponseException e) {
            if (e.getStatusCode() == 404) {
                throw new CustomException("This file could not be found. It may have been removed from storage.");
            }
            log.error("DRIVE: download failed for {}", fileId, e);
            throw unavailable();
        } catch (IOException e) {
            log.error("DRIVE: download failed for {}", fileId, e);
            throw unavailable();
        }
    }

    public void deleteQuietly(String fileId) {
        if (fileId == null || !isConfigured()) {
            return;
        }
        try {
            client().files().delete(fileId).execute();
        } catch (GoogleJsonResponseException e) {
            if (e.getStatusCode() != 404) {
                log.warn("DRIVE: could not delete {} (status {})", fileId, e.getStatusCode());
            }
        } catch (Exception e) {
            log.warn("DRIVE: could not delete {}: {}", fileId, e.getMessage());
        }
    }

    private Drive client() {
        if (!isConfigured()) {
            log.error("DRIVE: Google Drive storage is not configured. Set GOOGLE_DRIVE_CLIENT_ID, "
                    + "GOOGLE_DRIVE_CLIENT_SECRET and GOOGLE_DRIVE_REFRESH_TOKEN.");
            throw unavailable();
        }
        Drive local = drive;
        if (local == null) {
            synchronized (this) {
                local = drive;
                if (local == null) {
                    try {
                        UserCredentials credentials = UserCredentials.newBuilder()
                                .setClientId(clientId.trim())
                                .setClientSecret(clientSecret.trim())
                                .setRefreshToken(refreshToken.trim())
                                .build();
                        local = new Drive.Builder(
                                GoogleNetHttpTransport.newTrustedTransport(),
                                GsonFactory.getDefaultInstance(),
                                new HttpCredentialsAdapter(credentials))
                                .setApplicationName("Garbigo Auth Service")
                                .build();
                        drive = local;
                    } catch (GeneralSecurityException | IOException e) {
                        log.error("DRIVE: could not start the Google Drive client", e);
                        throw unavailable();
                    }
                }
            }
        }
        return local;
    }

    private static CustomException unavailable() {
        return new CustomException("We couldn't reach document storage right now. Please try again in a few minutes.");
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("'", "\\'");
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }
}