package de.deguo.wvsvetretungsplan

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.app.NotificationCompat
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import de.deguo.wvsvetretungsplan.databinding.FragmentFirstBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import java.io.File
import java.net.URL

class FirstFragment : Fragment() {

    private var _binding: FragmentFirstBinding? = null
    private lateinit var statusTextView: TextView
    private val url = "https://www.siemens-gymnasium-berlin.de/vertretungsplan"
    private val SUB_DIRECTORY = "SiemensGymPDFs"
    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!


    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFirstBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        statusTextView = binding.statusTextView

        binding.buttonFirst.setOnClickListener {
            downloadPdf()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun downloadPdf() {
        CoroutineScope(Dispatchers.Main).launch {
            statusTextView.text = "Searching PDF..."
            val pdfUrl = findPdfUrl()
            if (pdfUrl != null) {
                statusTextView.text = "PDF Found，download..."
                val file = downloadFile(pdfUrl, SUB_DIRECTORY)
                if (file != null) {
                    statusTextView.text = "PDF downdloaded successfully"
                    //showNotification(file)
                } else {
                    statusTextView.text = "PDF downloaded failed"
                }
            } else {
                statusTextView.text = "PDF not found"
            }
        }
    }

    private suspend fun findPdfUrl(): String? = withContext(Dispatchers.IO) {
        try {
            val doc = Jsoup.connect(url).get()
            val pdfLink = doc.select("a[href$=.pdf]").firstOrNull()
            pdfLink?.attr("abs:href")
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private suspend fun downloadFile(fileUrl: String, subDirectory: String): File? = withContext(Dispatchers.IO) {
        try {
            val url = URL(fileUrl)
            val connection = url.openConnection()
            connection.connect()

            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val customSubDir = File(downloadsDir, subDirectory)
            if (!customSubDir.exists()) {
                customSubDir.mkdirs()
            }

            val fileName = fileUrl.substring(fileUrl.lastIndexOf('/') + 1)
            val file = File(customSubDir, fileName)

            url.openStream().use { input ->
                file.outputStream().use { output ->
                    input.copyTo(output)
                }
            }

            withContext(Dispatchers.Main) {
                showNotification(file)
            }

            file
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun showNotification(file: File) {
        val notificationManager = requireContext().getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (!notificationManager.areNotificationsEnabled()) {
            // 通知权限未启用，引导用户前往设置
            val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            intent.putExtra(Settings.EXTRA_APP_PACKAGE, requireContext().packageName)
            startActivity(intent)
        }
        val channelId = "pdf_download_channel"
        val channelName = "PDF Downloads"

        // 创建通知渠道（Android 8.0 或更高版本需要）
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_DEFAULT)
            notificationManager.createNotificationChannel(channel)
        }

        // 创建用于打开 PDF 文件的 Intent
        val uri = FileProvider.getUriForFile(requireContext(), requireContext().packageName + ".fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW)
        intent.setDataAndType(uri, "application/pdf")
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        val pendingIntent = PendingIntent.getActivity(requireContext(), 0, intent, PendingIntent.FLAG_IMMUTABLE)

        // 创建通知
        val notification = NotificationCompat.Builder(requireContext(), channelId)
            .setContentTitle("PDF Downloaded")
            .setContentText(file.name)
            .setSmallIcon(android.R.drawable.stat_sys_download_done) // 使用适当的图标
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        // 显示通知
        notificationManager.notify(1, notification)
    }
}