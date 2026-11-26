package com.cdndash.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cdndash.app.data.AccountStore
import com.cdndash.app.domain.Account
import com.cdndash.app.ui.components.AppCard
import com.cdndash.app.ui.lock.rememberBiometricAvailability
import com.cdndash.app.ui.navigation.PillNavContentPadding
import com.cdndash.app.ui.navigation.TopBarContentPadding
import kotlinx.coroutines.launch

/**
 * 设置页（spec「页面结构」）：
 * - 账号列表：名称、启用开关、拖拽把手、删除（带确认）
 * - 新增账号表单：名称可自定义，空值/重名拒绝并提示
 * - 刷新间隔：5/10/15/30 分钟，默认 5，供票据 03 读取
 * - 生物识别解锁：默认关闭；设备验不了时隐藏开关并说明原因（票据 09）
 * - 关于：版本与「密钥只在本机」说明
 *
 * 视觉上按「账号 / 刷新间隔 / 安全 / 关于」分组成卡片，不再用分隔线硬切。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    accountStore: AccountStore,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.factory(accountStore)),
) {
    val accounts by viewModel.accounts.collectAsState()
    val refreshInterval by viewModel.refreshIntervalMinutes.collectAsState()
    val biometricEnabled by viewModel.biometricEnabled.collectAsState()
    val availability = rememberBiometricAvailability()
    val formError by viewModel.formError.collectAsState()
    var showAddDialog by rememberSaveable { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<Account?>(null) }
    val coroutineScope = rememberCoroutineScope()
    val appVersion = rememberAppVersion()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, top = TopBarContentPadding, end = 16.dp),
    ) {
        SectionHeader(title = "账号") {
            TextButton(
                onClick = {
                    viewModel.clearFormError()
                    showAddDialog = true
                },
            ) {
                Icon(
                    imageVector = Icons.Outlined.Add,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.size(4.dp))
                Text("新增")
            }
        }

        if (accounts.isEmpty()) {
            Text(
                text = "还没有账号。添加一个只读 AccessKey 后，看板会按这里的顺序展示。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 8.dp),
            )
        } else {
            AccountReorderList(
                accounts = accounts,
                onToggleEnabled = { account, enabled ->
                    viewModel.setEnabled(account.id, enabled)
                },
                onDelete = { account -> pendingDelete = account },
                onMove = viewModel::moveAccount,
            )
            Text(
                text = "长按左侧把手可拖拽排序，顺序不会随流量变化。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }

        SectionHeader(title = "刷新间隔")
        AppCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "打开应用与下拉时自动刷新，前台按所选间隔更新；退到后台后停止请求。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(top = 12.dp),
                ) {
                    AccountStore.REFRESH_INTERVAL_OPTIONS_MINUTES.forEach { minutes ->
                        FilterChip(
                            selected = minutes == refreshInterval,
                            onClick = { viewModel.setRefreshInterval(minutes) },
                            label = { Text("$minutes 分钟") },
                        )
                    }
                }
            }
        }

        SectionHeader(title = "安全")
        AppCard(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("生物识别解锁", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        text = if (availability.canShowSwitch) {
                            "开启后每次打开应用需验证一次指纹或面容，验证失败可回退到锁屏密码。"
                        } else {
                            // 设备验不了就不给开关，并说明为什么没有（spec「设备不支持时隐藏并说明」）。
                            availability.explanation ?: "本机不支持生物识别解锁。"
                        },
                        modifier = Modifier.padding(top = 4.dp, end = 12.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (availability.canShowSwitch) {
                    Switch(
                        checked = biometricEnabled,
                        onCheckedChange = viewModel::setBiometricEnabled,
                    )
                }
            }
        }

        SectionHeader(title = "关于")
        AppCard(Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("CDN 看板", style = MaterialTheme.typography.titleMedium)
                Text(
                    text = "版本 $appVersion",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row {
                    Icon(
                        imageVector = Icons.Outlined.Lock,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.size(8.dp))
                    Text(
                        text = "密钥仅加密保存在本机，不写入日志、不进系统备份；请为每个账号使用只读 AccessKey，手机丢失后请到阿里云控制台轮换。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        Spacer(Modifier.height(PillNavContentPadding))
    }

    if (showAddDialog) {
        AddAccountDialog(
            error = formError,
            onDismiss = {
                showAddDialog = false
                viewModel.clearFormError()
            },
            onConfirm = { name, accessKeyId, accessKeySecret ->
                coroutineScope.launch {
                    if (viewModel.addAccount(name, accessKeyId, accessKeySecret)) {
                        showAddDialog = false
                    }
                }
            },
        )
    }

    pendingDelete?.let { account ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("删除账号") },
            text = {
                Text("将删除「${account.name}」及其保存在本机的凭据与全部快照，此操作不可撤销。")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteAccount(account.id)
                        pendingDelete = null
                    },
                ) {
                    Text("删除")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text("取消")
                }
            },
        )
    }
}

@Composable
private fun SectionHeader(
    title: String,
    action: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 4.dp, top = 20.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        action?.invoke()
    }
}

@Composable
private fun AddAccountDialog(
    error: String?,
    onDismiss: () -> Unit,
    onConfirm: (name: String, accessKeyId: String, accessKeySecret: String) -> Unit,
) {
    var name by rememberSaveable { mutableStateOf("") }
    var accessKeyId by rememberSaveable { mutableStateOf("") }
    var accessKeySecret by rememberSaveable { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("新增账号") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("账号名称") },
                    placeholder = { Text("如「主站生产」") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = accessKeyId,
                    onValueChange = { accessKeyId = it },
                    label = { Text("AccessKeyId") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = accessKeySecret,
                    onValueChange = { accessKeySecret = it },
                    label = { Text("AccessKeySecret") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (error != null) {
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                Text(
                    text = "凭据仅用于本机直连阿里云 OpenAPI，请使用只读权限的 RAM 用户。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name, accessKeyId, accessKeySecret) },
            ) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        },
    )
}

@Composable
private fun rememberAppVersion(): String {
    val context = LocalContext.current
    return remember(context) {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull() ?: "1.0"
    }
}
