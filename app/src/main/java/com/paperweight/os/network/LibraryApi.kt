package com.paperweight.os.network

import com.paperweight.os.network.models.LibraryStructure
import retrofit2.http.GET

interface LibraryApi {
    @GET("/api/library/structure")
    suspend fun structure(): LibraryStructure
}
