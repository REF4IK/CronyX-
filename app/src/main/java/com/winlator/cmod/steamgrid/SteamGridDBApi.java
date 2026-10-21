package com.winlator.cmod.steamgrid;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Header;
import retrofit2.http.Path;
import retrofit2.http.Query;

public interface SteamGridDBApi {

    @GET("search/autocomplete/{term}")
    Call<SteamGridSearchResponse> searchGame(
            @Header("Authorization") String authHeader,
            @Path("term") String searchTerm
    );

    @GET("grids/game/{gameId}")
    Call<SteamGridGridsResponse> getGridsByGameId(
            @Header("Authorization") String authToken,
            @Path("gameId") int gameId,
            @Query("styles") String styles,
            @Query("dimensions") String dimensions,
            @Query("types") String types
    );

    @GET("grids/game/{gameId}")
    Call<SteamGridGridsResponse> getAnimatedGridsByGameId(
            @Header("Authorization") String authToken,
            @Path("gameId") int gameId,
            @Query("styles") String styles,
            @Query("dimensions") String dimensions,
            @Query("types") String types,
            @Query("mimes") String mimes,
            @Query("nsfw") String nsfw,
            @Query("humor") String humor
    );

    @GET("grids/steam/{appId}")
    Call<SteamGridGridsResponse> getGridsBySteamAppId(
            @Header("Authorization") String authToken,
            @Path("appId") int appId,
            @Query("styles") String styles,
            @Query("dimensions") String dimensions,
            @Query("types") String types
    );

    @GET("grids/steam/{appId}")
    Call<SteamGridGridsResponse> getAnimatedGridsBySteamAppId(
            @Header("Authorization") String authToken,
            @Path("appId") int appId,
            @Query("styles") String styles,
            @Query("dimensions") String dimensions,
            @Query("types") String types,
            @Query("mimes") String mimes,
            @Query("nsfw") String nsfw,
            @Query("humor") String humor
    );
}
