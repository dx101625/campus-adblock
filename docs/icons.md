# 图标来源与维护

导航、设备分类、设置、箭头、勾选和状态图标来自[Lucide](https://github.com/lucide-icons/lucide)，使用24px SVG、2px圆端描边，转换为Android VectorDrawable后按主题着色，无额外运行依赖。

原始SVG、路径及blob SHA固定在[tools/lucide-icons.json](../tools/lucide-icons.json)，可运行python tools/generate_icons.py重新转换；脚本不依赖联网。完整上游许可位于app/src/main/assets/licenses/LUCIDE-LICENSE.txt，保留Lucide ISC和Feather MIT的原始版权信息。应用内“关于 → 引用与许可 → Lucide”可阅读许可全文。

胖乖生活与智趣校园图标由PackageManager从本机已安装应用动态读取，不把目标应用的图片、APK或私有资源打入发布包。未安装或读取失败时用文字占位。返回模块时会刷新缓存和版本信息。

v1.5.2采用用户选定的①轮廓精修版，保留原版蓝底、白盾、横条与橙色斜线，遵循本项目MIT许可。源图固定在[app/artwork/launcher.svg](../app/artwork/launcher.svg)。运行python tools/generate_launcher.py可从同一几何生成Android自适应前景矢量、五档密度普通与圆形PNG和1024px预览；Pillow只用于开发时生成图标，不是App构建或运行依赖。生成预览已与用户所选候选图逐像素比对。GitHub Octicons、Miuix和Backdrop继续使用各自项目标识及原许可。
