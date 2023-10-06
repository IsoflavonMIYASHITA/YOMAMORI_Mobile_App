package com.example.yomamoriagentappforandroid

import android.content.Context
import android.database.Cursor
import android.provider.ContactsContract
import android.util.Log
import java.lang.Exception
import java.net.URL
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import javax.net.ssl.HttpsURLConnection
import kotlin.concurrent.thread

//連絡先リスト照合
class CustomContentReader(incomingNumber: String){
    //PROJECTIONの設定（SQLのSELECT文(射影)に相当）
    private val phoneNumberProjection: Array<String> = arrayOf(
        ContactsContract.CommonDataKinds.Phone.NUMBER
    )
    //selectionClauseの設定（SQLのwhere句に相当）
    private val incoming: String? = incomingNumber.replace("+81", "0")
    private var selectionClause: String? = ContactsContract.CommonDataKinds.Phone.NUMBER + "= '" + incoming + "'"
    //ContactsContract.Data.MIMETYPE + "='" + Phone.CONTENT_ITEM_TYPE + "'"

    fun queryPhoneList(context: Context): Cursor? {
        var phoneCursor: Cursor? = context.contentResolver.query(
            ContactsContract.Data.CONTENT_URI,
            phoneNumberProjection,
            selectionClause,
            null,
            null
        )

        return phoneCursor
    }

    fun verifyQueryResult(phoneCursor: Cursor?):Int {
        //件数確認（0件（電話帳に存在しない）の場合はAPIへのリクエスト）
        return when (phoneCursor?.count) {
            null -> 0
            0 -> 1
            else -> 0
        }
    }

}