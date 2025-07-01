package com.arashjahani.mappolygonpointsdraw.data.repository

import com.arashjahani.mappolygonpointsdraw.data.model.LandParcel
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

class LandRepository {
    private val db = FirebaseFirestore.getInstance()

    suspend fun addLand(land: LandParcel) {
        db.collection("lands").add(land).await()
    }

    suspend fun getAllLands(): List<LandParcel> {
        val snapshot = db.collection("lands").get().await()
        return snapshot.toObjects(LandParcel::class.java)
    }
}