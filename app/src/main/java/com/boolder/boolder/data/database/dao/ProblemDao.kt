package com.boolder.boolder.data.database.dao

import androidx.room.Dao
import androidx.room.Query
import com.boolder.boolder.data.database.entity.ProblemEntity
import com.boolder.boolder.data.database.entity.ProblemWithAreaName

@Dao
interface ProblemDao {

    @Query("SELECT * FROM problems WHERE id == :problemId")
    suspend fun loadById(problemId: Int): ProblemEntity?

    @Query("SELECT * FROM problems WHERE id IN (:problemIds)")
    suspend fun loadAllByIds(problemIds: List<Int>): List<ProblemEntity>

    @Query(
        """
        SELECT problems.*, areas.name AS 'areaName' FROM problems, areas 
        WHERE problems.name_searchable LIKE :name AND problems.area_id = areas.id
        ORDER BY problems.popularity DESC 
        LIMIT 20
        """
    )
    suspend fun problemsByName(name: String): List<ProblemWithAreaName>

    @Query("SELECT * FROM problems WHERE id = :id")
    suspend fun problemById(id: Int): ProblemEntity?

    @Query("SELECT * FROM problems WHERE parent_id = :parentProblemId")
    suspend fun problemVariantsByParentId(parentProblemId: Int): List<ProblemEntity>

    @Query(
        """
        SELECT id FROM problems 
        WHERE circuit_id = :circuitId AND circuit_number = :circuitProblemNumber
        """
    )
    suspend fun problemIdByCircuitAndNumber(
        circuitId: Int,
        circuitProblemNumber: Int
    ): Int?

    //passing grades in the form ('1a','1b','1c', '2a') i.e. List<String>
    @Query(
        """
        SELECT problems.* FROM problems, areas  
        WHERE problems.grade IN (:grades)
            AND problems.area_id = areas.id
            AND areas.name_searchable LIKE :name
        ORDER BY problems.grade ASC
        """
    )
    suspend fun getProblemsByAreaAndGrade(name: String, grades: List<String>): List<ProblemEntity>

    @Query(
        """
        SELECT problems.*, areas.name AS 'areaName' FROM problems, areas 
	    WHERE problems.area_id = areas.id
	        AND areas.name_searchable LIKE :name
        """
    )
    suspend fun getAllProblemsByArea(name: String): List<ProblemEntity>
}
