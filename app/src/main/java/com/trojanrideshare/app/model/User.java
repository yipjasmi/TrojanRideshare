package com.trojanrideshare.app.model;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentId;

public class User {
    @DocumentId public String id;
    public String fullName;
    public String email;
    public String uscId;
    public int gradYear;
    public Timestamp createdAt;

    public User() {}
}
