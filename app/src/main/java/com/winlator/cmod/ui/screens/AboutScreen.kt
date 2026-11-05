package com.winlator.cmod.ui.screens

import com.winlator.cmod.ui.components.GlassSectionHeader
import androidx.compose.foundation.shape.RoundedCornerShape
import com.winlator.cmod.ui.components.glassCardFill
import com.winlator.cmod.ui.components.glassCardBorder
import com.winlator.cmod.ui.components.glassTitleColor
import com.winlator.cmod.ui.components.GlassTokens
import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.winlator.cmod.BuildConfig
import com.winlator.cmod.R

@Composable
fun AboutScreen() {
    val uriHandler = LocalUriHandler.current
    val primary = MaterialTheme.colorScheme.primary
    val bodyColor = MaterialTheme.colorScheme.onSurfaceVariant
    val linkStyle = SpanStyle(color = primary, textDecoration = TextDecoration.Underline)

    fun link(text: String, url: String): AnnotatedString = buildAnnotatedString {
        pushStringAnnotation("URL", url)
        withStyle(linkStyle) { append(text) }
        pop()
    }

    @Composable
    fun SectionCard(title: String, content: AnnotatedString) {
        // Референс: название секции стоит НАД карточкой, а не внутри неё.
        GlassSectionHeader(title = title)
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = glassCardFill(), contentColor = glassTitleColor()),
            shape = RoundedCornerShape(GlassTokens.CardRadius),
            border = glassCardBorder(),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                ClickableText(
                    text = content,
                    style = MaterialTheme.typography.bodyMedium.copy(color = bodyColor, lineHeight = 24.sp),
                    onClick = { offset ->
                        content.getStringAnnotations("URL", offset, offset).firstOrNull()?.let {
                            try { uriHandler.openUri(it.item) } catch (_: Exception) {}
                        }
                    },
                )
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Header
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = glassCardFill(), contentColor = glassTitleColor()),
            shape = RoundedCornerShape(GlassTokens.CardRadius),
            border = glassCardBorder(),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Image(
                    painter = painterResource(R.drawable.logowi),
                    contentDescription = "Logo",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                )
                Spacer(Modifier.height(8.dp))
                val context = LocalContext.current
                Text(
                    text = "Version " + getVersionFromApkName(context),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = bodyColor,
                )
            }
        }

        // About App and Mod Author
        SectionCard(stringResource(R.string.about_app_and_mod_author), buildAnnotatedString {
            append("Winlator Bionic REF4IK MOD by ref4ik\n")
            append("Telegram: "); append(link("t.me/winlatorruu", "https://t.me/winlatorruu"))
            append("\nGitHub: "); append(link("github.com/REF4IK/CronyX-", "https://github.com/REF4IK/CronyX-"))
        })

        // Creators
        SectionCard(stringResource(R.string.credits_and_third_party_apps), buildAnnotatedString {
            append(link("Winlator by brunodev85", "https://github.com/brunodev85/winlator")); append("\n")
            append(link("Winlator Bionic by Pipetto-crypto", "https://github.com/Pipetto-crypto/winlator/tree/dev")); append("\n")
            append(link("Winlator Cmod by Coffincolors", "https://github.com/coffincolors/winlator")); append("\n")
            append(link("Winlator Bionic Ludashi by StevenMXZ", "https://github.com/StevenMXZ/Winlator-Ludashi")); append("\n")
            append(link("Bannerlator by The412Banner", "https://github.com/The412Banner/Bannerlator")); append("\n")
            append(link("WinNative by WinNative Organization", "https://github.com/WinNative-Emu")); append("\n")
            append(link("GameNative by Utkarshdalal", "https://github.com/utkarshdalal/GameNative"))
        })

        // Components
        SectionCard("Components", buildAnnotatedString {
            append(link("Ubuntu RootFs (Focal Fossa)", "https://releases.ubuntu.com/20.04/")); append("\n")
            append(link("Wine (winehq.org)", "https://www.winehq.org/")); append("\n")
            append(link("Wine Proton", "https://github.com/ValveSoftware/Proton")); append("\n")
            append("Box86/Box64 by "); append(link("ptitseb", "https://github.com/ptitseb")); append("\n")
            append(link("FEX-Emu", "https://github.com/FEX-Emu/FEX")); append("\n")
            append(link("PRoot", "https://proot-me.github.io/")); append("\n")
            append(link("Mesa (Turnip/Zink/VirGL)", "https://www.mesa3d.org/")); append("\n")
            append(link("DXVK", "https://github.com/doitsujin/dxvk")); append("\n")
            append(link("VKD3D", "https://gitlab.winehq.org/wine/vkd3d")); append("\n")
            append(link("D8VK", "https://github.com/AlpyneDreams/d8vk")); append("\n")
            append(link("linux-fg", "https://github.com/xXJSONDeruloXx/linux-fg")); append("\n")
            append(link("CNC DDraw", "https://github.com/FunkyFr3sh/cnc-ddraw")); append("\n")
            append(link("WinlatorWCFHub by Arihany", "https://github.com/Arihany/WinlatorWCFHub")); append("\n")
            append(link("GLIBC Patches by Termux Pacman", "https://github.com/termux-pacman/glibc-packages")); append("\n")
            append(link("Leegao Wrapper by leegao", "https://github.com/leegao")); append("\n")
            append(link("LSFG via WinNative", "https://github.com/WinNative-Emu")); append("\n")
            append(link("lsfg-vk upstream", "https://github.com/PancakeTAS/lsfg-vk")); append("\n")
            append(link("LSFG Eden Emulator", "https://github.com/eden-emulator"))
        })

        // GPU Drivers
        SectionCard("GPU Drivers", buildAnnotatedString {
            append(link("K11MCH1 AdrenoTools", "https://github.com/K11MCH1/AdrenoToolsDrivers")); append("\n")
            append(link("MrPurple666 Purple Turnip", "https://github.com/MrPurple666/purple-turnip")); append("\n")
            append(link("Weab-chan Freedreno Turnip CI", "https://github.com/Weab-chan/freedreno_turnip-CI")); append("\n")
            append(link("crueter GameHub 8Elite", "https://github.com/crueter/GameHub-8Elite-Drivers")); append("\n")
            append(link("StevenMXZ Adreno Drivers", "https://github.com/StevenMXZ/Adreno-Tools-Drivers"))
        })

        // Glibc
        SectionCard(stringResource(R.string.glibc_exp_edition), buildAnnotatedString {
            append("longjunyu2's ("); append(link("Fork", "https://github.com/longjunyu2")); append(")")
        })
    }
}

/**
 * Версия из имени установленного APK (например "CronyX-7.1.5x-cmod.apk" → "7.1.5x"),
 * чтобы версию можно было менять простым переименованием APK при подписи.
 * Если в имени версии нет — фолбэк на BuildConfig.VERSION_NAME.
 */
private fun getVersionFromApkName(context: Context): String {
    val fileName = try {
        context.packageManager.getApplicationInfo(context.packageName, 0)
            .sourceDir?.substringAfterLast('/') ?: ""
    } catch (e: Exception) {
        ""
    }
    val match = Regex("""\d+\.\d+(\.\d+)?[a-zA-Z]*""").find(fileName)
    return match?.value ?: BuildConfig.VERSION_NAME
}
