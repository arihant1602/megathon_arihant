# Roboto Flex source and license

These bundled static TrueType fonts are derived from the official Google Fonts Roboto Flex distribution. They are licensed under SIL Open Font License 1.1. The original copyright notice and complete license are included in OFL.txt and retained in font metadata. The source license specifies no Reserved Font Names.

Source revision: `8b0a1d0f5983c89bc2b93f1b5fb55f9e252744b5`
Source: https://github.com/google/fonts/tree/8b0a1d0f5983c89bc2b93f1b5fb55f9e252744b5/ofl/robotoflex
Source file: `RobotoFlex[GRAD,XOPQ,XTRA,YOPQ,YTAS,YTDE,YTFI,YTLC,YTUC,opsz,slnt,wdth,wght].ttf`

Source SHA-256: `9b523f7d82593df0107173849ebb8c817471a1df4b4fb2c3cbf40cfd810c8281`

Generated with fontTools 4.66.1, `fontTools.varLib.instancer.instantiateVariableFont`, freezing every axis to its default except weight and optical size listed below. Width is 100, slant and grade are 0. Names 1/2/4/6/16/17 are set to Roboto Flex Regular, Roboto Flex Medium, or Roboto Flex Display Regular as appropriate. No glyph subsetting.

| Asset in res/font | Weight | Optical size | SHA-256 |
| --- | --- | --- | --- |
| `roboto_flex_regular.ttf` | 400 | 14 | `5d8b8eede363534cb57f121d4c282a3df65606fc65f60bd7519d0fff5a0f2f58` |
| `roboto_flex_medium.ttf` | 500 | 14 | `8430ea1bf548d3d460548ee2883bd8cf92be47b5f1d7bd302a6ada5868798d1e` |
| `roboto_flex_display_regular.ttf` | 400 | 48 | `565ffe9f5189fc0c9cb0e7f511097839918b46c4083799590f1c1fefb1493ddd` |

Reproduction: load the pinned TTF with `TTFont`; set `axes = {a.axisTag: a.defaultValue for a in font["fvar"].axes}`; update `wght`/`opsz`; call `instantiateVariableFont(font, axes, inplace=False, updateFontNames=False)`; set name IDs 1/16 to the family, 2/17 to the subfamily, 4 to family plus subfamily and 6 to the PostScript name (no spaces); save to the listed filename.

The display asset approximates the regular editorial appearance requested for Sentinel AI. It is Roboto Flex, not Alfabet. No Qualcomm or proprietary Alfabet font assets are included. Static fonts avoid requiring Android API 26 variable-font support and are bundled for offline API 23+ rendering. The font generation tool is not an application or build dependency.
