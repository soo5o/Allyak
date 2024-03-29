package com.example.allyak


data class ItemData (
    var docId : String? = null,
    var UID: String? = null,
    var title: String? = null,
    var content: String? = null,
    var date: String? = null,
    var viewdate: String? = null,
    var likeCnt: Int = 0,
    var commentCnt: Int = 0,
    var comments: Map<String, Comments> = emptyMap(),
    var like: Map<String, LikeData> = emptyMap()
)
data class Comments(
    var postId:String? = null,
    var docId: String? = null, // 댓글의 고유 ID
    var userId: String? = null, // 댓글 작성자의 고유 ID
    var content: String? = null,   // 댓글 내용
    var time: String? = null  // 댓글 작성 시간
)
data class LikeData(
    var userId: String? = null
)
data class MyInfo(
    var key: String? = null,
    var medinow: Boolean = false,
    var medinot: Boolean = false,
    var pillName: String? = null,
    var pillMemo: String? = null
)