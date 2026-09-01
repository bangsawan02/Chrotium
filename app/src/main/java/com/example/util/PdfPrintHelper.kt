package com.example.util

import android.content.Context
import android.print.PrintAttributes
import android.print.PrintManager
import android.webkit.WebView
import android.widget.Toast

/**
 * Utility helper untuk mencetak atau menyimpan halaman WebView saat ini ke format PDF
 * menggunakan Android PrintManager & WebView PrintDocumentAdapter bawaan OS.
 */
object PdfPrintHelper {

    fun printPageToPdf(context: Context, webView: WebView?, title: String?) {
        if (webView == null) {
            Toast.makeText(context, "Halaman belum siap untuk dicetak", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val printManager = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager
            if (printManager == null) {
                Toast.makeText(context, "Layanan Cetak / PDF tidak tersedia pada perangkat ini", Toast.LENGTH_SHORT).show()
                return
            }

            val documentName = (title?.takeIf { it.isNotBlank() } ?: "Dokumen_Web")
                .replace(Regex("[^a-zA-Z0-9._ -]"), "_")
                .take(40)

            val printAdapter = webView.createPrintDocumentAdapter(documentName)
            val printAttributes = PrintAttributes.Builder()
                .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
                .setResolution(PrintAttributes.Resolution("pdf_print", "PDF Export", 300, 300))
                .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
                .build()

            printManager.print(documentName, printAdapter, printAttributes)
        } catch (e: Exception) {
            Toast.makeText(context, "Gagal membuka menu cetak PDF: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }
}
