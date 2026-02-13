package com.android.permissioncontroller.ext.gmscore

import android.app.compat.gms.GmsCorePackageFlag
import android.content.pm.ApplicationInfo
import android.content.pm.GosPackageState
import android.ext.PackageId
import android.permission.PermissionManager
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceGroup
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import com.android.permissioncontroller.R
import com.android.permissioncontroller.ext.BaseGosPkgStateConfigFragment
import android.content.pm.PackageManager
import com.android.permissioncontroller.ext.BaseSettingsActivity
import com.android.permissioncontroller.ext.addCategory
import com.android.permissioncontroller.permission.ui.handheld.PermissionsCollapsingToolbarBaseFragment

class GmsCoreConfigActivity : BaseSettingsActivity() {
    override fun getNavGraphStart() = R.id.gmscore_config
}

class GmsCoreConfigWrapperFragment : PermissionsCollapsingToolbarBaseFragment() {
    override fun createPreferenceFragment(): PreferenceFragmentCompat = GmsCoreConfigFragment()
}

class GmsCoreConfigFragment : BaseGosPkgStateConfigFragment(
    packageName = PackageId.GMS_CORE_NAME,
    titleStringRes = R.string.gmscore_settings
) {
    override fun configurePreferenceScreen(screen: PreferenceScreen) {
        screen.addCategory(R.string.rcs_activation_category).apply {
            addPkgFlagPermReversed(
                this, GmsCorePackageFlag.GRANT_PERMS_FOR_ICC_AUTHENTICATION,
                R.string.gmscore_icc_auth_perms_title,
                R.string.gmscore_icc_auth_perms_confirm,
            )
        }
    }

    /**
     * Add a package flag permission with REVERSED logic: toggle ON = disable feature, toggle OFF = enable feature.
     * This makes the feature enabled by default (when flag is not set).
     */
    private fun addPkgFlagPermReversed(
        dst: PreferenceGroup,
        flag: Int,
        @StringRes title: Int,
        @StringRes confirmationText: Int,
        @StringRes summary: Int = 0
    ): SwitchPreferenceCompat {
        val pref = SwitchPreferenceCompat(dst.context)
        pref.setTitle(title)
        if (summary != 0) {
            pref.setSummary(summary)
        }

        pkgFlagPrefs[flag] = pref

        pref.onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _, newValueB ->
            val newValue = newValueB as Boolean
            val ctx = requireContext()

            if (newValue) {
                AlertDialog.Builder(ctx).run {
                    setMessage(getText(confirmationText))
                    setPositiveButton(R.string.cancel, null)
                    setNegativeButton(android.R.string.ok) { _, _ ->
                        updatePackageFlag(flag, true)  // Set flag = disable feature
                    }
                    show()
                }
                false
            } else {
                updatePackageFlag(flag, false)
               true
            }
        }

        dst.addPreference(pref)
        return pref
    }

    private fun updatePackageFlag(flag: Int, flagValue: Boolean) {
        val ctx = requireContext()
        val userId = android.os.Process.myUserHandle().identifier
        
        GosPackageState.edit(packageName, userId).run {
            setPackageFlagState(flag, flagValue)
            applyOrPressBack()
        }

        val permManager = ctx.getSystemService(PermissionManager::class.java)!!
        permManager.updatePermissionState(packageName, userId)

        GosPackageState.edit(packageName, userId).run {
            killUidAfterApply()
            applyOrPressBack()
        }

        val isPkgEnabled = pkgManager.getApplicationInfo(packageName, 0).enabled
        if (isPkgEnabled) {
            pkgManager.setApplicationEnabledSetting(packageName, PackageManager.COMPONENT_ENABLED_STATE_DISABLED, userId)
            pkgManager.setApplicationEnabledSetting(packageName, PackageManager.COMPONENT_ENABLED_STATE_ENABLED, userId)
        }
    }

    override fun updateNonPkgStateUi(applicationInfo: ApplicationInfo) {}
}
