/*
 * Copyright (C) 2019 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.role.controller.behavior;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Process;
import android.os.UserHandle;
import android.util.ArraySet;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.modules.utils.build.SdkLevel;
import com.android.role.controller.model.Permissions;
import com.android.role.controller.model.Role;
import com.android.role.controller.model.RoleBehavior;
import com.android.role.controller.util.CollectionUtils;
import com.android.role.controller.util.PackageUtils;
import com.android.role.controller.util.UserUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Class for behavior of the browser role.
 *
 * @see com.android.settings.applications.DefaultAppSettings
 * @see com.android.settings.applications.defaultapps.DefaultBrowserPreferenceController
 * @see com.android.settings.applications.defaultapps.DefaultBrowserPicker
 * @see com.android.server.pm.PackageManagerService#resolveAllBrowserApps(int)
 */
public class BrowserRoleBehavior implements RoleBehavior {
    private static final Intent BROWSER_INTENT = new Intent()
            .setAction(Intent.ACTION_VIEW)
            .addCategory(Intent.CATEGORY_BROWSABLE)
            .setData(Uri.fromParts("http", "", null));

    @Nullable
    @Override
    public String getFallbackHolder(@NonNull Role role, @NonNull Context context) {
        UserHandle user = Process.myUserHandle();
        List<String> qualifyingPackageNames = getQualifyingPackagesAsUserInternal(null, false, user,
                context);
        if (qualifyingPackageNames.size() == 1) {
            return qualifyingPackageNames.get(0);
        }

        if (SdkLevel.isAtLeastS()) {
            List<String> qualifyingSystemPackageNames = getQualifyingPackagesAsUserInternal(null,
                    true, user, context);
            if (qualifyingSystemPackageNames.size() == 1) {
                return qualifyingSystemPackageNames.get(0);
            }

            List<String> defaultPackageNames = role.getDefaultHolders(context);
            return CollectionUtils.firstOrNull(defaultPackageNames);
        } else {
            return null;
        }
    }

    // PackageManager.queryIntentActivities() will only return the default browser if one was set.
    // Code in the Settings app passes PackageManager.MATCH_ALL and perform its own filtering, so we
    // do the same thing here.
    @Nullable
    @Override
    public List<String> getQualifyingPackagesAsUser(@NonNull Role role, @NonNull UserHandle user,
            @NonNull Context context) {
        return getQualifyingPackagesAsUserInternal(null, false, user, context);
    }

    @Nullable
    @Override
    public Boolean isPackageQualified(@NonNull Role role, @NonNull String packageName,
            @NonNull Context context) {
        List<String> packageNames = getQualifyingPackagesAsUserInternal(packageName, false,
                Process.myUserHandle(), context);
        return !packageNames.isEmpty();
    }

    @NonNull
    private List<String> getQualifyingPackagesAsUserInternal(@Nullable String packageName,
            boolean matchSystemOnly, @NonNull UserHandle user, @NonNull Context context) {
        Context userContext = UserUtils.getUserContext(context, user);
        PackageManager userPackageManager = userContext.getPackageManager();
        Intent intent = BROWSER_INTENT;
        if (packageName != null) {
            intent = new Intent(intent)
                    .setPackage(packageName);
        }
        // To one's surprise, MATCH_ALL doesn't include MATCH_DIRECT_BOOT_*.
        int flags = PackageManager.MATCH_ALL | PackageManager.MATCH_DIRECT_BOOT_AWARE
                | PackageManager.MATCH_DIRECT_BOOT_UNAWARE | PackageManager.MATCH_DEFAULT_ONLY;
        if (matchSystemOnly) {
            flags |= PackageManager.MATCH_SYSTEM_ONLY;
        }
        List<ResolveInfo> resolveInfos = userPackageManager.queryIntentActivities(intent, flags);
        ArraySet<String> packageNames = new ArraySet<>();
        int resolveInfosSize = resolveInfos.size();
        for (int i = 0; i < resolveInfosSize; i++) {
            ResolveInfo resolveInfo = resolveInfos.get(i);

            if (!resolveInfo.activityInfo.exported || !resolveInfo.handleAllWebDataURI) {
                continue;
            }
            packageNames.add(resolveInfo.activityInfo.packageName);
        }
        return new ArrayList<>(packageNames);
    }
}
