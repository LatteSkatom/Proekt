package com.example.proekt.network;

import com.google.gson.annotations.SerializedName;

public class LoginResponse {
    @SerializedName("success")
    private boolean success;

    @SerializedName("message")
    private String message;

    @SerializedName("user_id")
    private int userId;

    @SerializedName("login")
    private String login;

    public boolean isSuccess() { return success; }
    public String getMessage() { return message; }
    public int getUserId() { return userId; }
    public String getLogin() { return login; }
}
