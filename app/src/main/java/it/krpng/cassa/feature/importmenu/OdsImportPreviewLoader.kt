package it.krpng.cassa.feature.importmenu

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import it.krpng.cassa.data.ods.OdsImportPreviewProcessor
import it.krpng.cassa.data.ods.OdsImportPreviewResult
import it.krpng.cassa.domain.repository.AdditionRepository
import it.krpng.cassa.domain.repository.ProductRepository
import java.io.FileNotFoundException
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

interface OdsImportPreviewLoader {
    suspend fun load(documentUri: String): OdsImportPreviewResult
}

class OdsDocumentOpenException : FileNotFoundException(
    "Non è possibile aprire il file ODS selezionato.",
)

class AndroidOdsImportPreviewLoader @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val productRepository: ProductRepository,
    private val additionRepository: AdditionRepository,
) : OdsImportPreviewLoader {
    private val processor = OdsImportPreviewProcessor()

    override suspend fun load(documentUri: String): OdsImportPreviewResult =
        withContext(Dispatchers.IO) {
            try {
                val input = context.contentResolver.openInputStream(Uri.parse(documentUri))
                    ?: throw OdsDocumentOpenException()
                input.use { stream ->
                    processor.createPreview(
                        input = stream,
                        existingProducts = productRepository.observeAll().first(),
                        existingAdditions = additionRepository.observeAll().first(),
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: OdsDocumentOpenException) {
                throw error
            } catch (_: FileNotFoundException) {
                throw OdsDocumentOpenException()
            } catch (_: SecurityException) {
                throw OdsDocumentOpenException()
            }
        }
}
