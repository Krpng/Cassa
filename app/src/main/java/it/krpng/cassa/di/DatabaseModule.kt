package it.krpng.cassa.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import it.krpng.cassa.data.database.CassaDatabase
import it.krpng.cassa.data.database.CassaMigrations
import it.krpng.cassa.data.database.DatabaseTransactionRunner
import it.krpng.cassa.data.database.RoomDatabaseTransactionRunner
import it.krpng.cassa.data.database.dao.AdditionDao
import it.krpng.cassa.data.database.dao.AppSettingsDao
import it.krpng.cassa.data.database.dao.IngredientDao
import it.krpng.cassa.data.database.dao.NumberingStateDao
import it.krpng.cassa.data.database.dao.OrderDao
import it.krpng.cassa.data.database.dao.ProductDao
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context,
    ): CassaDatabase = Room.databaseBuilder(
        context,
        CassaDatabase::class.java,
        DATABASE_NAME,
    )
        .addMigrations(CassaMigrations.MIGRATION_1_2)
        .build()

    @Provides
    fun provideProductDao(database: CassaDatabase): ProductDao = database.productDao()

    @Provides
    fun provideAdditionDao(database: CassaDatabase): AdditionDao = database.additionDao()

    @Provides
    fun provideIngredientDao(database: CassaDatabase): IngredientDao = database.ingredientDao()

    @Provides
    fun provideOrderDao(database: CassaDatabase): OrderDao = database.orderDao()

    @Provides
    fun provideNumberingStateDao(database: CassaDatabase): NumberingStateDao =
        database.numberingStateDao()

    @Provides
    fun provideAppSettingsDao(database: CassaDatabase): AppSettingsDao =
        database.appSettingsDao()

    @Provides
    fun provideDatabaseTransactionRunner(
        database: CassaDatabase,
    ): DatabaseTransactionRunner = RoomDatabaseTransactionRunner(database)

    private const val DATABASE_NAME = "cassa.db"
}
