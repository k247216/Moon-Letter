package com.twomemory.app

import java.time.LocalDate

internal fun nextSharedOccasion(cards: List<OccasionCard>,couple: String,user: String,today: LocalDate): OccasionCard? =
    cards.asSequence().filter {it.coupleId==couple && it.visibility=="SHARED" &&
        (it.kind=="BIRTHDAY" || it.kind=="ANNIVERSARY")}
        .mapNotNull {card -> com.twomemory.app.notifications.CloudKeepsakeReminderRules.nextDate(card,user,today)?.let {card to it}}
        .sortedWith(compareBy<Pair<OccasionCard,LocalDate>>({it.second},{it.first.id}))
        .firstOrNull()?.first
