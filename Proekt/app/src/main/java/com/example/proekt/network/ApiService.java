package com.example.proekt.network;

import retrofit2.Call;
import retrofit2.http.Field;
import retrofit2.http.FormUrlEncoded;
import retrofit2.http.Body;
import retrofit2.http.POST;
import retrofit2.http.Headers;
import com.google.gson.JsonObject;

public interface ApiService {

    // ----------- JSON POST -----------
    @Headers("Content-Type: application/json")
    @POST("add_subscription.php")
    Call<SimpleResponse> addSubscription(@Body JsonObject subData);


    // ----------- REGISTRATION -----------
    @FormUrlEncoded
    @POST("register.php")
    Call<RegisterResponse> register(
            @Field("username") String username,
            @Field("email") String email,
            @Field("password") String password
    );


    // ----------- GET SUBSCRIPTIONS -----------
    @FormUrlEncoded
    @POST("get_subscriptions.php")
    Call<SubscriptionResponse> getSubscriptions(
            @Field("user_id") int userId
    );


    // ----------- LOGIN -----------
    @FormUrlEncoded
    @POST("login.php")
    Call<LoginResponse> login(
            @Field("usernameOrEmail") String usernameOrEmail,
            @Field("password") String password
    );


    // ----------- DELETE SUB -----------
    @FormUrlEncoded
    @POST("delete_subscription.php")
    Call<DeleteResponse> deleteSubscription(
            @Field("id_sub") int idSub,
            @Field("user_id") int userId
    );

}
