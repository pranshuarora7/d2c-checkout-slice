package com.kinetic.checkout.repository;

import com.kinetic.checkout.dto.ProfileDtos;
import com.kinetic.checkout.exception.ApiException;
import com.kinetic.checkout.model.Profile;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class ProfileRepository {
    private final JdbcTemplate jdbc;

    public ProfileRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Profile get(String userId) {
        List<Profile.Address> addresses =
                jdbc.query(
                        "SELECT * FROM addresses WHERE user_id = ? AND active = 1 ORDER BY rowid",
                        (r, n) ->
                                new Profile.Address(
                                        r.getString("id"),
                                        r.getString("label"),
                                        r.getString("address_line"),
                                        r.getString("city"),
                                        r.getString("state"),
                                        r.getString("pin_code")),
                        userId);
        return jdbc
                .query(
                        "SELECT * FROM profiles WHERE id = ?",
                        (r, n) ->
                                new Profile(
                                        r.getString("name"),
                                        r.getString("email"),
                                        r.getString("phone"),
                                        addresses),
                        userId)
                .stream()
                .findFirst()
                .orElseThrow(() -> ApiException.notFound("Profile"));
    }

    public void updateContact(String userId, ProfileDtos.Contact contact) {
        jdbc.update(
                "UPDATE profiles SET name=?, email=?, phone=? WHERE id=?",
                contact.name(),
                contact.email(),
                contact.phone(),
                userId);
    }

    public void addAddress(String userId, String id, ProfileDtos.AddressInput address) {
        jdbc.update(
                "INSERT INTO addresses (id,user_id,label,address_line,city,state,pin_code) VALUES"
                        + " (?,?,?,?,?,?,?)",
                id,
                userId,
                address.label(),
                address.addressLine(),
                address.city(),
                address.state(),
                address.pinCode());
    }

    public void updateAddress(String userId, String id, ProfileDtos.AddressInput address) {
        int changed =
                jdbc.update(
                        "UPDATE addresses SET label=?,address_line=?,city=?,state=?,pin_code=?"
                                + " WHERE id=? AND user_id=? AND active=1",
                        address.label(),
                        address.addressLine(),
                        address.city(),
                        address.state(),
                        address.pinCode(),
                        id,
                        userId);
        if (changed == 0) throw ApiException.notFound("Address");
    }

    public void removeAddress(String userId, String id) {
        // Keep the referenced row so previously placed orders retain their original snapshot.
        if (jdbc.update("UPDATE addresses SET active=0 WHERE id=? AND user_id=?", id, userId)
                == 0) {
            throw ApiException.notFound("Address");
        }
    }
}
