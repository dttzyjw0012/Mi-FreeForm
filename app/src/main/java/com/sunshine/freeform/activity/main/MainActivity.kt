package com.sunshine.freeform.activity.main

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.sunshine.freeform.R
import com.sunshine.freeform.activity.mi_window_setting.MiWindowSettingActivity
import com.sunshine.freeform.activity.permission.PermissionActivity
import com.sunshine.freeform.service.floating.FloatingService
import com.sunshine.freeform.utils.PermissionUtils
import com.sunshine.freeform.utils.ServiceUtils
import com.sunshine.freeform.utils.TagUtils
import kotlinx.android.synthetic.main.activity_main.*


/**
 * @author sunshine
 * @date 2021/1/31
 */
class MainActivity : AppCompatActivity(), View.OnClickListener {

    private lateinit var viewModel: MainViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        Toast.makeText(this, "f", Toast.LENGTH_LONG).show()
        viewModel = ViewModelProvider(this).get(MainViewModel::class.java)

        requirePermission()

        button_freeform_setting.setOnClickListener(this)
    }

    /**
     * 请求权限类
     */
    private fun requirePermission() {
        //如果有权限没有打开，去授权界面
        val overlays = try {
            Settings.canDrawOverlays(this)
        }catch (e: Exception) {
            Toast.makeText(this, getString(R.string.cannot_granted_overlay), Toast.LENGTH_LONG).show()
            false
        }
        val notification = try {
            Settings.Secure.getString(contentResolver, "enabled_notification_listeners").contains(packageName)
        }catch (e: Exception) {
            Toast.makeText(this, getString(R.string.cannot_granted_notification), Toast.LENGTH_LONG).show()
            false
        }
        val usage = try {
            (getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager).checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), packageName) == AppOpsManager.MODE_ALLOWED
        }catch (e: Exception) {
            Toast.makeText(this, getString(R.string.cannot_granted_usage), Toast.LENGTH_LONG).show()
            false
        }
        val accessibility = PermissionUtils.hasAccessibility(this)

        if (!overlays || !notification || !usage || !accessibility) {
            //注意这个tag必须要大于等于0，否则会失效
            startActivityForResult(Intent(this, PermissionActivity::class.java), TagUtils.MAIN_ACTIVITY)
        } else if (viewModel.isShowFloating() && !ServiceUtils.isServiceWork(this, "{$packageName}.service.floating.FloatingService")) {
            //如果服务没有运行就启动
            startService(Intent(applicationContext, FloatingService::class.java))
            viewModel.getAllFreeFormApps().observe(this, Observer {
                FloatingService.floatingApps = it
            })
        }
    }

    private fun deniedDialog() {
        val builder = MaterialAlertDialogBuilder(this)
        builder.setTitle(getString(R.string.dialog_title))
        builder.setMessage(getString(R.string.permission_fail))
        builder.setPositiveButton(getString(R.string.to_grant)) { _, _ ->
            startActivityForResult(Intent(this, PermissionActivity::class.java), TagUtils.MAIN_ACTIVITY)
        }
        builder.setNegativeButton(getString(R.string.to_finish)) { _, _ ->
            finish()
        }
        builder.setCancelable(false)
        builder.create().show()
    }

    override fun onClick(v: View?) {
        when(v?.id) {
            R.id.button_freeform_setting -> {
                startActivity(Intent(this, MiWindowSettingActivity::class.java))
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == TagUtils.MAIN_ACTIVITY) {
            when(resultCode) {
                RESULT_OK -> {
                    Toast.makeText(this, getString(R.string.permission_success), Toast.LENGTH_SHORT).show()
                    //如果服务没有开启，就把设置设为关闭
                    if (viewModel.isShowFloating() && !ServiceUtils.isServiceWork(this, "{$packageName}.service.floating.FloatingService")) {
                        viewModel.closeService()
                    }
                }
                RESULT_CANCELED -> {
                    deniedDialog()
                }
            }
        }
    }
}