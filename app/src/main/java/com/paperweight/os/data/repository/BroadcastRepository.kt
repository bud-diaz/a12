package com.paperweight.os.data.repository

class BroadcastRepository(
    val vaultRepository: VaultRepository,
    val scheduleRepository: ScheduleRepository,
    val stationRepository: StationRepository,
)
