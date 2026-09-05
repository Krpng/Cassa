package it.krpng.cassa.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import it.krpng.cassa.feature.importmenu.AndroidOdsImportPreviewLoader
import it.krpng.cassa.feature.importmenu.OdsImportPreviewLoader
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class OdsImportModule {
    @Binds
    @Singleton
    abstract fun bindOdsImportPreviewLoader(
        loader: AndroidOdsImportPreviewLoader,
    ): OdsImportPreviewLoader
}
