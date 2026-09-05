package it.krpng.cassa.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import it.krpng.cassa.data.database.CassaDatabase
import it.krpng.cassa.data.database.dao.AdditionDao
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
    ).build()

    @Provides
    fun provideProductDao(database: CassaDatabase): ProductDao = database.productDao()

    @Provides
    fun provideAdditionDao(database: CassaDatabase): AdditionDao = database.additionDao()

    private const val DATABASE_NAME = "cassa.db"
}
