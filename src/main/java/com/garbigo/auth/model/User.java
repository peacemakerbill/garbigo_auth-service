package com.garbigo.auth.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

@Data
@Document(collection = "users")
public class User implements UserDetails {

    // UserDetails extends Serializable; without this, IDEs flag "class does not
    // declare a serialVersionUID". Spring Security never actually serializes this
    // object here (sessions are STATELESS), so the value itself doesn't matter -
    // it just needs to exist.
    private static final long serialVersionUID = 1L;

    @Id
    private String id;

    // Named displayUsername (not "username") so Lombok can actually generate a
    // getter/setter for it. getUsername() below is the UserDetails contract method
    // and intentionally returns the email instead - if this field were also called
    // "username", Lombok would silently skip generating its accessor because
    // getUsername() already exists, leaving the field impossible to read back out.
    // @Field/@JsonProperty keep the Mongo document key and the JSON wire format as
    // "username" - only the Java identifier changed, not stored data or the API shape.
    @Field("username")
    @JsonProperty("username")
    private String displayUsername;

    private String firstName;
    private String middleName;
    private String lastName;

    @Indexed(unique = true)           // Unique index on email
    private String email;

    @Indexed(unique = true)           // Unique index on phoneNumber
    private String phoneNumber;

    private String homeAddress;
    private String password;
    private String profilePictureUrl;

    private Role role = Role.CLIENT;

    private String wastePreferences;
    private String collectionSchedule;

    private boolean verified = false;
    private boolean active = true;
    private boolean archived = false;

    private List<String> followers;
    private List<String> likes;
    private List<String> reviews;
    private List<String> liveLocations;

    @CreatedDate
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    @Override
    public String getUsername() {
        return email;   // Using email as username for Spring Security
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return active;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return verified && active && !archived;
    }
}