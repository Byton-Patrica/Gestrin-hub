package dev.gestrin.hub

import dev.gestrin.hub.ui.AppText
import org.junit.Assert.*
import org.junit.Test

class CopyTest {
    @Test fun chineseCopyIsUnchanged() { assertEquals("游戏库", AppText.render("zh", "游戏库")) }
    @Test fun englishCopyCoversCorePagesAndControls() {
        listOf("游戏库", "运行器", "设置", "未验证兼容性", "选择目录", "检查启动条件", "校验信息", "备份", "语言").forEach {
            assertNotEquals(it, AppText.render("en", it))
        }
    }
    @Test fun formattedCopyPreservesRawValues() {
        assertEquals("Downloading 1.20.4 · 2 MB / 43 MB", AppText.render("en", "正在下载 1.20.4 · 2 MB / 43 MB"))
        assertEquals("Imported log line 12: 设置", AppText.render("en", "导入日志第 12 行：设置"))
        assertEquals("org.ppsspp.ppsspp", AppText.render("en", "org.ppsspp.ppsspp"))
        assertEquals("file:///目录/game.cso", AppText.render("en", "file:///目录/game.cso"))
    }
    @Test fun diagnosticsTranslateStructureWithoutRewritingLogExcerpts() {
        assertEquals("Evidence: Imported log line 4: 运行器", AppText.render("en", "依据：导入日志第 4 行：运行器"))
        assertEquals("Source unavailable\nEvidence: The provider could not confirm that the source still exists.", AppText.render("en", "来源不可用\n依据：文件提供方无法确认所选来源仍然存在。"))
    }
    @Test fun importSummaryTranslatesCountsAndWarnings() {
        assertEquals("Added 2 games; skipped 1 duplicates; some sources need renewed access or review", AppText.render("en", "已加入 2 款，跳过 1 个重复来源；部分来源需重新授权或手动检查"))
    }
}
