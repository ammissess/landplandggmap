package com.hung.landplanggmap.data.repository

import com.hung.landplanggmap.data.model.LandParcel
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import com.google.firebase.auth.FirebaseAuth


class LandRepository {
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    suspend fun addLand(land: LandParcel) {
        db.collection("lands").add(land).await()
    }
    suspend fun getUserLands(): List<LandParcel> {
        val uid = auth.currentUser?.uid ?: return emptyList()
        val snapshot = db.collection("lands")
            .whereEqualTo("createdBy", uid)
            .get()
            .await()
        return snapshot.toObjects(LandParcel::class.java)
    }
    suspend fun getAllLands(): List<LandParcel> {
        val snapshot = db.collection("lands").get().await()
        return snapshot.toObjects(LandParcel::class.java)
    }
}