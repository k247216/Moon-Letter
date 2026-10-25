package com.twomemory.app

import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class UpcomingOccasionTest {
    private val today=LocalDate.of(2026,10,11)
    private fun card(id:String,date:String,kind:String="ANNIVERSARY",visibility:String="SHARED",couple:String="space")=
        OccasionCard(id,couple,"me",kind,id,"秘密正文",date,true,null,null,"",visibility)
    @Test fun heroUsesTheNearestRealBirthdayOrAnniversaryNotAnOldPreference() {
        val cards=listOf(card("生日","2020-05-09","BIRTHDAY"),card("约定的日子","2020-10-12"))
        assertEquals("约定的日子",nextSharedOccasion(cards,"space","me",today)?.title)
        assertEquals("生日",nextSharedOccasion(cards,"space","me",LocalDate.of(2026,10,13))?.title)
    }
    @Test fun privateSurprisesForeignSpaceExpiredOrMalformedCardsCannotBecomeTheHero() {
        val cards=listOf(card("私密","2020-10-11",visibility="PRIVATE"),card("惊喜","2020-10-11",kind="SURPRISE"),
            card("其他空间","2020-10-11",couple="other"),card("坏日期","bad"),
            card("不再重复","2020-10-11").copy(repeatsYearly=false),card("正常","2020-10-20"))
        assertEquals("正常",nextSharedOccasion(cards,"space","me",today)?.id)
        assertNull(nextSharedOccasion(emptyList(),"space","me",today))
    }
}
