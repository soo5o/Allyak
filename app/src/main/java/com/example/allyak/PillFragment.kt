package com.example.allyak

import android.app.AlarmManager
import android.app.AlertDialog
import android.app.PendingIntent
import android.content.Context.ALARM_SERVICE
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener
import com.kakao.sdk.user.UserApiClient
import com.prolificinteractive.materialcalendarview.CalendarDay
import com.prolificinteractive.materialcalendarview.DayViewDecorator
import com.prolificinteractive.materialcalendarview.DayViewFacade
import com.prolificinteractive.materialcalendarview.MaterialCalendarView
import com.prolificinteractive.materialcalendarview.spans.DotSpan
import java.lang.Thread.sleep
import kotlin.collections.set
import kotlin.system.exitProcess

class PillFragment : Fragment() {
    lateinit var calendar : MaterialCalendarView
    lateinit var pillListAdapter: PillListAdapter
    lateinit var recyclerView: RecyclerView
    lateinit var pillsList: ArrayList<PillListInfo> //서버에서 가져온 모든 알약 정보를 담은 리스트
    private lateinit var userId: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        UserApiClient.instance.me { user, error ->
            if (error != null) {
                Log.e("##INFO", "사용자 정보 요청 실패", error)
            } else if (user != null) {
                userId = user.id.toString()
            }
        }
        requestPermission()
    }
    //알람 권한을 요청하는 함수
    private fun requestPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    requireContext().applicationContext,
                    android.Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED) {
                if (ActivityCompat.shouldShowRequestPermissionRationale(
                        requireActivity(),
                        android.Manifest.permission.POST_NOTIFICATIONS
                    )) {
                    // 이미 권한을 거절한 경우 권한 설정 화면으로 이동
                    val intent: Intent =
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).setData(
                            Uri.parse("package:" + requireActivity().packageName)
                        )
                    startActivity(intent)
                    requireActivity().finish()
                } else {
                    // 처음 권한 요청을 할 경우
                    registerForActivityResult(ActivityResultContracts.RequestPermission()) {
                        when (it) {
                            true -> {
                                Toast.makeText(context, "권한이 수락되었습니다", Toast.LENGTH_SHORT).show()
                            }
                            false -> {
                                Toast.makeText(context, "권한을 수락해주세요", Toast.LENGTH_SHORT).show()
                                requireActivity().moveTaskToBack(true)
                                requireActivity().finishAndRemoveTask()
                                exitProcess(0)
                            }
                        }
                    }.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        }
    }
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_pill, container, false)
        calendar = view.findViewById(R.id.cal_calendar)
        calendar.selectedDate = CalendarDay.today() // 오늘 날짜로 초기화
        recyclerView = view.findViewById(R.id.re_pill_list)
        pillsList = ArrayList()
        return view
    }
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        UserApiClient.instance.me { user, error ->
            if (error != null) {
                Log.e("##INFO", "사용자 정보 요청 실패", error)
            } else if (user != null) {
                userId = user.id.toString()
                requestPillData(userId)
            }
        }
        setEvent()
        sendFCM()
    }

    //FCM을 통해 알림을 보내는 함수
    private fun sendNotificationFCM(pillList: ArrayList<PillListInfo>) {
    }

    //파이어베이스에서 알림 데이터를 가져오는 함수
    private fun requestPillData(userId : String) {
        //데이터 베이스에서 데이터 읽기
        val selectedDate = calendar.selectedDate
        val date = "${selectedDate?.year}${selectedDate?.month}${selectedDate?.day}"

        MyRef.alarmRef.child(userId).child(date)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(dataSnapshot: DataSnapshot) {
                    //기존 데이터 삭제
                    pillsList.clear()
                    for (postSnapshot in dataSnapshot.children) {
                        val key = postSnapshot.key.toString()
                        val alarmname = postSnapshot.child("mediName").getValue(String::class.java)
                        val alarmhour = postSnapshot.child("hour").getValue(Int::class.java)
                        val alarmminute = postSnapshot.child("minute").getValue(Int::class.java)
                        val alarmYear = postSnapshot.child("calendarYear").getValue(Int::class.java)
                        val alarmMonth = postSnapshot.child("calendarMonth").getValue(Int::class.java)
                        val alarmDay = postSnapshot.child("calendarDay").getValue(Int::class.java)
                        val isChecked = postSnapshot.child("checked").getValue(Boolean::class.java)
                        if (alarmname != null && alarmhour != null && alarmminute != null &&
                            alarmYear != null && alarmMonth != null && alarmDay != null && isChecked != null
                        ) {
                            // 서버에서 가져온 알약 정보를 리스트에 추가
                            pillsList.add(
                                PillListInfo(
                                    key,
                                    alarmname,
                                    alarmhour,
                                    alarmminute,
                                    alarmYear,
                                    alarmMonth,
                                    alarmDay,
                                    isChecked
                                )
                            )
                        }
                    }
                    Log.i("##INFO", "pillList.size = ${pillsList.size}");
                    checkPillChecked()
                    comparePillData()
                    // 데이터 변경 후에 어댑터에 변경 내용을 알려줌
                    pillListAdapter.notifyDataSetChanged()
                }

                override fun onCancelled(databaseError: DatabaseError) {
                    Log.e("TAG", "Failed to read value.", databaseError.toException())
                }
            })
    }

    private fun sendFCM() {
        Thread {
            sleep(3000)
            sendNotificationFCM(pillsList)
        }.start()
    }

    //알림을 체크한 경우 달력에 체크한 날짜를 표시하는 함수
    private fun checkPillChecked() {
        var allChecked = true
        Log.d("Allyakkk", "list: $pillsList")
        pillsList.forEach {
            val itemYear = it.calendarYear
            val itemMonth = it.calendarMonth
            val itemDay = it.calendarDay
            val checked = it.checked
            val date = CalendarDay.from(itemYear, itemMonth, itemDay)
            if (!checked) {
                allChecked = false
            }
            if(!allChecked){
                // allSelected가 false일 때 장식 제거
                decoratedDates[date]?.let {
                    calendar.removeDecorator(it)
                    decoratedDates.remove(date)
                }
            }else{
                val newDecorator = object : DayViewDecorator {
                    override fun shouldDecorate(day: CalendarDay?): Boolean {
                        return day == date
                    }
                    override fun decorate(view: DayViewFacade?) {
                        view?.addSpan(DotSpan(5f, Color.BLUE))
                    }
                }
                // 이전에 추가된 장식이 있으면 제거
                decoratedDates[date]?.let { calendar.removeDecorator(it) }
                // 새로운 장식 추가
                calendar.addDecorator(newDecorator)
                decoratedDates[date] = newDecorator
            }
        }
    }

    var clickCount = 0
    var clickTime = 0L
    var selectedDate = ""

    //달력을 클릭했을 때 동작하는 함수
    private fun setEvent() {
        calendar.setOnDateChangedListener { widget, date, selected ->
            if (calendar.selectedDate.toString() == selectedDate) {
                clickCount++
            } else {
                clickCount = 1
            }

            val currentTime = System.currentTimeMillis()
            if (clickCount == 1) {
                clickTime = currentTime
                selectedDate = calendar.selectedDate.toString()
                comparePillData()
                requestPillData(userId)
            } else if (clickCount >= 2 && selectedDate == calendar.selectedDate.toString()) {
                val diff = currentTime - clickTime
                if (diff < 1000) {
                    clickCount = 0
                    //val selectedDate = "${date.year}-${date.month}-${date.day}"
                    val i = Intent(requireContext(), AddAlarmActivity::class.java)
                    i.putExtra("selectedYear", date.year)
                    i.putExtra("selectedMonth", date.month)
                    i.putExtra("selectedDayOfMonth", date.day)
                    startActivity(i)
                } else {
                    clickCount = 0
                    clickTime = 0
                }
            }
        }
    }
    private val decoratedDates = HashMap<CalendarDay, DayViewDecorator>()
    //선택한 날짜에 해당하는 알림 데이터를 가져와서 리사이클러 뷰에 표시하기
    private fun comparePillData() {
        val selectedDate = calendar.selectedDate
        val selectedYear = selectedDate?.year
        val selectedMonth = selectedDate?.month
        val selectedDay = selectedDate?.day

        val selectedPillList = ArrayList<PillListInfo>()
        for (i in pillsList) {
            if (i.calendarYear == selectedYear && i.calendarMonth == selectedMonth && i.calendarDay == selectedDay) {
                selectedPillList.add(i)
            }
        }
        pillListAdapter = PillListAdapter(selectedPillList, object : PillListAdapter.OnItemClickListener {
            override fun onItemClick(data: PillListInfo, position: Int) {
                //기존 데이터의 데이터를 변경해주기 위한 for문
                pillsList.forEach {
                    if (it.mediName == data.mediName) {
                        it.checked = data.checked
                    }
                }
                pillsList.reverse()
                val key = data.key

                val pill = Pill(
                    data.key,
                    data.mediName,
                    data.hour,
                    data.minute,
                    data.calendarYear,
                    data.calendarMonth,
                    data.calendarDay,
                    data.checked
                )
                MyRef.alarmRef.child(userId)
                    .child("${data.calendarYear}${data.calendarMonth}${data.calendarDay}")
                    .child(key).setValue(pill)

                var isAllChecked = true
                // 모든 아이템이 체크되었는지 확인
                selectedPillList.forEach {
                    if (it.checked == false) {
                        isAllChecked = false
                    }
                }
                // 체크한 아이템을 달력에 표시
                val date =
                    CalendarDay.from(data.calendarYear, data.calendarMonth, data.calendarDay)

                // 모든 아이템이 체크되었을 때
                if (isAllChecked) {
                    // 새로운 장식 생성
                    val newDecorator = object : DayViewDecorator {
                        override fun shouldDecorate(day: CalendarDay?): Boolean {
                            return day == date
                        }

                        override fun decorate(view: DayViewFacade?) {
                            view?.addSpan(DotSpan(5f, Color.BLUE)) //파란점
                        }
                    }
                    // 이전에 추가된 장식이 있으면 제거
                    decoratedDates[date]?.let { calendar.removeDecorator(it) }
                    // 새로운 장식 추가
                    calendar.addDecorator(newDecorator)
                    decoratedDates[date] = newDecorator
                } else {
                    // allSelected가 false일 때 장식 제거
                    decoratedDates[date]?.let {
                        calendar.removeDecorator(it)
                        decoratedDates.remove(date)
                    }
                }
            }
            //아이템을 삭제하는 함수
            override fun onItemDelete(data: PillListInfo, position: Int) {
                val dialog = AlertDialog.Builder(requireContext())
                dialog.setTitle("알림 삭제")
                dialog.setMessage("${data.mediName}의 알림을 삭제하시겠습니까?")
                dialog.setPositiveButton("삭제") { dialog, which ->
                    val key = data.key
                    MyRef.alarmRef.child(userId)
                        .child("${data.calendarYear}${data.calendarMonth}${data.calendarDay}")
                        .child(key).removeValue()
                    selectedPillList.removeAt(position)
                    pillListAdapter.updateItems(selectedPillList)
                    pillsList.remove(data)

                    // 그날의 데이터가 비었을때 dotSpan 제거
                    if (selectedPillList.isEmpty()) {
                        val date = CalendarDay.from(
                            data.calendarYear,
                            data.calendarMonth,
                            data.calendarDay
                        )
                        decoratedDates[date]?.let {
                            calendar.removeDecorator(it)
                            decoratedDates.remove(date)
                        }
                    }
                    val notificationID =  data.calendarYear+ data.calendarMonth + data.calendarDay + data.hour + data.minute

                    cancelNotification(notificationID)

                }
                dialog.setNegativeButton("취소") { dialog, which ->
                    dialog.dismiss()
                }
                dialog.show()
            }
        })
        //리사이클러뷰에 어댑터 설정
        recyclerView.adapter = pillListAdapter
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
    }
    /**
     * 알람을 삭제하는 함수
     */
    private fun cancelNotification(notificationID: Int) {
        val intent = Intent(requireContext(), Notification::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            requireContext(),
            notificationID,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val alarmManager = requireContext().getSystemService(ALARM_SERVICE) as AlarmManager
        alarmManager.cancel(pendingIntent)

        pendingIntent.cancel()
    }

    override fun onResume() {
        super.onResume()
        if (::pillsList.isInitialized) {
            UserApiClient.instance.me { user, error ->
                if (error != null) {
                    Log.e("##INFO", "사용자 정보 요청 실패", error)
                } else if (user != null) {
                    userId = user.id.toString()
                    requestPillData(userId)
                }
            }
        }
    }
}