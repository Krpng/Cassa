package it.krpng.cassa.data.database

import androidx.room.Database
import androidx.room.RoomDatabase
import it.krpng.cassa.data.database.dao.OrderDao
import it.krpng.cassa.data.database.dao.ProductDao
import it.krpng.cassa.data.database.entity.AdditionEntity
import it.krpng.cassa.data.database.entity.AppSettingsEntity
import it.krpng.cassa.data.database.entity.IngredientEntity
import it.krpng.cassa.data.database.entity.NumberingStateEntity
import it.krpng.cassa.data.database.entity.OrderEntity
import it.krpng.cassa.data.database.entity.OrderItemAdditionEntity
import it.krpng.cassa.data.database.entity.OrderItemEntity
import it.krpng.cassa.data.database.entity.OrderItemRemovalEntity
import it.krpng.cassa.data.database.entity.ProductEntity
import it.krpng.cassa.data.database.entity.ProductIngredientEntity

@Database(
    entities = [
        ProductEntity::class,
        IngredientEntity::class,
        AdditionEntity::class,
        ProductIngredientEntity::class,
        OrderEntity::class,
        OrderItemEntity::class,
        OrderItemAdditionEntity::class,
        OrderItemRemovalEntity::class,
        AppSettingsEntity::class,
        NumberingStateEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class CassaDatabase : RoomDatabase() {
    abstract fun productDao(): ProductDao

    abstract fun orderDao(): OrderDao
}
