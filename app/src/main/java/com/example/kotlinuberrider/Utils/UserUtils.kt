package com.example.kotlinuberrider.Utils

import android.content.Context
import android.view.View
import android.widget.Toast
import com.example.kotlinuberrider.Common.Common
import com.example.kotlinuberrider.Model.Token
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase

object UserUtils {
    fun updateUser(view: View, data: Map<String, Any>) {
        FirebaseDatabase
            .getInstance()
            .getReference(Common.RIDER_INFO_REFERENCE)
            .child(FirebaseAuth.getInstance().currentUser!!.uid)
            .updateChildren(data)
            .addOnFailureListener { e->
                Snackbar.make(view, e.message!!, Snackbar.LENGTH_SHORT).show()
            }
            .addOnSuccessListener{
                Snackbar.make(view, "Update success!", Snackbar.LENGTH_SHORT).show()
            }
    }

    fun updateToken(context: Context, token: String) {
        val tokenModel = Token()
        tokenModel.token = token

        FirebaseDatabase
            .getInstance()
            .getReference(Common.TOKEN_REFERENCE)
            .child(FirebaseAuth.getInstance().currentUser!!.uid)
            .setValue(tokenModel)
            .addOnFailureListener { e->
                Toast.makeText(context, e.message, Toast.LENGTH_SHORT).show()
            }
            .addOnSuccessListener {  }
    }
}