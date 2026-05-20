package com.mtg.commander.data.repository

import com.mtg.commander.data.dao.PlayerDao
import com.mtg.commander.data.entity.PlayerEntity
import com.mtg.commander.domain.model.Player
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class PlayerRepository(private val playerDao: PlayerDao) {

    fun getAllPlayers(): Flow<List<Player>> =
        playerDao.getAllPlayers().map { it.map(PlayerEntity::toDomain) }

    suspend fun getPlayerById(id: Long): Player? =
        playerDao.getPlayerById(id)?.toDomain()

    suspend fun insertPlayer(name: String): Long =
        playerDao.insertPlayer(PlayerEntity(name = name.trim()))

    suspend fun updatePlayer(player: Player) =
        playerDao.updatePlayer(player.toEntity())

    suspend fun deletePlayer(player: Player) =
        playerDao.deletePlayer(player.toEntity())
}

private fun PlayerEntity.toDomain() = Player(id = id, name = name, createdAt = createdAt)
private fun Player.toEntity() = PlayerEntity(id = id, name = name, createdAt = createdAt)
