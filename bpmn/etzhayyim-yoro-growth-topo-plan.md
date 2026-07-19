# etzhayyim × yoro.etzhayyim.com — 事業成長推進 逆トポロジーソート設計

作成日: 2026-04-20
対象: 契約メンバー全員 × Claude Cowork × yoro.etzhayyim.com

---

## 設計思想

**yoro.etzhayyim.com = 事業成長の可視化ハブ**

各メンバーが Claude Cowork でタスクを実行 → AT Protocol 記録 → yoro.etzhayyim.com のフィードに可視化
→ 社会的信頼・ブランド認知・ネットワーク効果 → 新規顧客獲得 → 事業成長 (MRR↑)

---

## 逆トポロジーソート (Goal → Root)

```
L9: [最終目標] 事業成長 — 売上拡大・MRR向上・新規顧客獲得
      ↑ depends on
L8: [受注・成約] 顧客信頼の獲得・提案→契約クローズ (CLO k.bakshi × CEO j.kawasaki)
      ↑ depends on
L7: [社会的可視性] yoro.etzhayyim.com での実績公開・ブランド確立 (全メンバー AT 投稿)
      ↑ depends on
L6: [専門成果物] 各専門ドメインの成果生成・品質確認 (parallel: Cyber/Brand/Legal/Tech/Creative)
      ↑ depends on
L5: [プロセス完遂] BPMN フォーム全ステップ完了・納品確認 (COO a.nakamura)
      ↑ depends on
L4: [タスク実行] Claude Cowork サポート下での各メンバータスク実行
      ↑ depends on
L3: [プロセス開始] kaisya.startProcess → vertex_human_task 登録 → Teams 案内
      ↑ depends on
L2: [案件受付] COO 案件受付 × CEO 戦略方針 → DMN ルーティング
      ↑ depends on
L1: [機会発掘] yoro.etzhayyim.com フィード監視・外部シグナル検知・顧客アプローチ
```

---

## L1–L9 詳細: メンバー × Claude Cowork × yoro

### L1 — 機会発掘 (Market Signal Detection)

**担当**: j.kawasaki (CEO) × a.nakamura (COO)

| Claude Cowork アクション | yoro 統合 |
|---|---|
| `「今週 yoro のフィードで新しい繋がりは？」` | yoro フォロワー増加・DM 着信を `listMyFormTasks` + Graph で確認 |
| `「業界の最新動向を要約して」` | yoro Discover タブ + Bluesky フィード集約 |
| `「n.takahashi の cybersecurity 投稿を AT で検索して」` | `app.bsky.feed.searchPosts` で社内メンバー投稿参照 |

**AT Record**: `com.etzhayyim.apps.kaisya.opportunitySignal` → yoro feed に可視化

---

### L2 — 案件受付・戦略方針 (Project Intake)

**担当**: a.nakamura (COO) + j.kawasaki (CEO)

| Claude Cowork アクション | yoro 統合 |
|---|---|
| `「project-intake フォームを開いて」` → kaisya.etzhayyim.com/forms/project-intake | フォーム送信 → AT Repo 記録 → yoro フィードに「案件開始」投稿 |
| `「DMN でこの案件のルーティングを確認して」` | `com.etzhayyim.apps.kaisya.routeDecision` 結果を AT Repo 記録 |
| `「a.nakamura の今週のタスクを確認して」` | `listMyFormTasks(assigneeDid="did:web:kaisya.etzhayyim.com:member:a-nakamura")` |

---

### L3 — プロセス開始 (startProcess)

**担当**: a.nakamura (COO) ← Teams bot 自動 or Claude Cowork 経由

| Claude Cowork アクション | yoro 統合 |
|---|---|
| `「業務開始: 案件名」` → Teams bot → kaisya.startProcess | プロセス開始 AT 記録 → yoro フィードに「BPMN プロセス開始」可視化 |
| `「startProcess を呼んで projectName=XXX で」` | 11 タスク vertex_human_task 登録 + sendBpmnGuidance |

---

### L4 — タスク実行 (Claude Cowork 支援)

**各メンバーの Claude Cowork 利用パターン**:

#### j.kawasaki (CEO) — 戦略 × 最終承認
```
「自分のタスクを確認して」
→ listMyFormTasks(role=ceo) → Step A (戦略方針) + Step L (最終承認)

「project-intake フォームのStrategic Alignment 欄を書くのを手伝って」
→ Claude が会社コンテキスト (vertex_company_profile) 参照して下書き作成

「Step L の最終承認チェックリストを全項目確認して」
→ checkLegal/checkDelivery/checkSecurity/checkCreative/checkDev を順に確認
```
**yoro**: CEO の戦略投稿 → フォロワー向け思想リーダーシップ → 採用・顧客信頼

#### a.nakamura (COO) — 案件管理 × 納品確認
```
「今週の全案件の進捗を表にして」
→ listMyFormTasks(role=coo, status=pending) + graph で案件ステータス取得

「Step K の delivery-confirm フォームを開いて」
→ kaisya.etzhayyim.com/forms/delivery-confirm に誘導

「この案件、k.bakshi に契約締結をお願いして」
→ sendTeamsMessage でタスクリマインド + vertex_human_task priority 更新
```
**yoro**: COO の業務進捗投稿 → 透明性・組織信頼 → パートナー獲得

#### k.bakshi (CLO) — 契約 × 法務最終確認
```
「LingLing 案件の ZeLo 7問への回答を下書きして」
→ Claude が legal-case フォームデータ + ZeLo API 参照して回答案生成

「Step C の contract-review フォームを開いて」
→ kaisya.etzhayyim.com/forms/contract-review に誘導

「インド法務案件の自動ルーティングを確認して」
→ com.etzhayyim.apps.lawfirm.createCase の autoRoute 結果確認
```
**yoro**: CLO の法務ナレッジ投稿 → 法的信頼性 → エンタープライズ顧客獲得

#### n.takahashi (Cybersecurity事業部責任者)
```
「Step E の security-assessment フォームを埋めるのを手伝って」
→ threatLevel / affectedSystems / mitigationPlan の下書き生成

「この案件の脅威評価を MITRE ATT&CK フレームワークで分析して」
→ Claude が security コンテキスト参照して構造化分析

「今月の Cybersecurity 評価サマリーを yoro に投稿する文章を作って」
→ 成果の AT 投稿下書き生成
```
**yoro**: Cyber評価実績の公開 → セキュリティ信頼性証明 → Cybersecurity 事業売上

#### t.ichihara (Branding事業部責任者)
```
「Step F の creative-brief フォームの Brand Strategy セクションを書いて」
→ targetAudience / keyMessage 下書き支援

「競合ブランド分析をして、差別化ポイントを整理して」
→ yoro Discover + web 検索で市場ポジショニング

「今月のブランド活動レポートを AT に記録して」
→ `com.etzhayyim.apps.kaisya.brandingActivity` AT Record 生成
```
**yoro**: ブランドコンテンツ投稿 → 認知拡大 → Branding 事業リード獲得

#### k.takahashi (クリエイティブディレクター)
```
「Step G の creative-brief の Creative Direction を t.ichihara の Brand Strategy に合わせて書いて」
→ F→G 依存関係を考慮した内容生成

「クライアント向けクリエイティブ提案書の outline を作って」
→ Deliverables taglist の内容から提案書構成生成

「今月の制作物を yoro にまとめてポートフォリオ投稿して」
→ AT Repo 投稿下書き → yoro ポートフォリオページ
```
**yoro**: クリエイティブポートフォリオ公開 → 案件引き合い増加

#### t.chikada (CS部)
```
「Step H の security-assessment の Tech Requirements を書いて」
→ affectedSystems / techRequirements セクション支援

「この案件の技術スタックと要件を整理して」
→ vertex_human_task の関連データ参照して技術要件書生成

「CS 部の技術ブログ記事を下書きして yoro に投稿して」
→ AT Repo 記録 → yoro テックフィード
```
**yoro**: 技術実績の公開 → 技術力証明 → エンジニアリング案件獲得

#### f.tanaka / y.nishino (エンジニア)
```
「Step I の delivery-confirm フォームの Completed Items を書いて」
→ 実装完了物の列挙支援

「この sprint の完了タスクをまとめてリリースノートにして」
→ vertex_human_task の完了記録から自動生成

「今月の開発成果を AT Repo に記録して yoro に公開して」
→ 開発実績 AT Record → yoro エンジニアフィード
```
**yoro**: 開発成果の公開 → 技術者採用・パートナー連携

---

### L5 — プロセス完遂 (BPMN 全ステップ完了)

**担当**: a.nakamura (COO) — 納品確認 Step K

**Claude Cowork**:
```
「この案件の全ステップの完了状況を確認して」
→ listMyFormTasks(projectRef=processInstanceId, status=completed) で棚卸し

「未完了のステップを担当者にリマインドして」
→ sendTeamsMessage で対象者にリマインド
```

---

### L6 — 専門成果物 (Parallel Tracks)

**5 並列トラック — 全て Claude Cowork 経由で yoro に記録**:

| トラック | 担当 | 成果物 | yoro 公開形式 |
|---|---|---|---|
| Cybersecurity | n.takahashi | 脅威評価レポート | `com.etzhayyim.apps.kaisya.securityReport` AT Record |
| Branding | t.ichihara | ブランド戦略書 | `app.bsky.feed.post` (Bluesky 互換投稿) |
| Legal | k.bakshi | 契約書・法務意見書 | `com.etzhayyim.apps.kaisya.legalNote` AT Record (hash only, Tier 1) |
| Creative | k.takahashi | クリエイティブ制作物 | `app.bsky.feed.post` + blob 添付 |
| Engineering | f.tanaka/y.nishino | 実装 PR・リリースノート | `com.etzhayyim.apps.kaisya.devRelease` AT Record |

---

### L7 — yoro.etzhayyim.com での可視性 (Social Proof Hub)

**yoro での統合表示**:

```
yoro Discover タブ
  ├── etzhayyim メンバーフィード (follow-based)
  │     ├── CEO: 戦略・ビジョン投稿
  │     ├── COO: 案件進捗・業務透明性
  │     ├── CLO: 法務ナレッジ
  │     ├── Cybersecurity: 評価実績
  │     ├── Branding: クリエイティブ作品
  │     └── Engineering: 開発成果
  └── 社外フォロワー (顧客候補・パートナー) が閲覧
        → 専門性認知 → 問い合わせ → L8 受注へ
```

**yoro Agent Profile (Interactive KAMI LiveStage)**:
- 各メンバーの AT DID に紐づく Agent Profile ページ
- MCP tool discovery: 訪問者が「このメンバーに相談する」ボタン → Claude Cowork DM 起動
- 実績 AT Record が yoro Profile ページにタイムライン表示

---

### L8 — 顧客信頼・受注 (Customer Trust → Contracts)

**担当**: k.bakshi (CLO) × j.kawasaki (CEO)

**Claude Cowork**:
```
「この見込み客のプロフィールと過去の yoro でのインタラクションを確認して」
→ yoro graph の follow/like/comment エッジを参照

「提案書のエグゼクティブサマリーを書いて」
→ 案件コンテキスト + CEO 戦略方針を統合した提案文生成

「契約締結に必要な法務書類のチェックリストを確認して」
→ contract-review フォームデータ参照
```

---

### L9 — 事業成長 (Business Growth)

**KPI → yoro.etzhayyim.com で可視化**:

| KPI | AT Record | yoro 表示 |
|---|---|---|
| 新規案件数 | `com.etzhayyim.apps.kaisya.processStarted` | CEO/COO ダッシュボード |
| 成約率 | BPMN Step L 完了数 / Step A 開始数 | 月次レポート投稿 |
| MRR | `com.etzhayyim.apps.kaisya.contractSigned` 金額合計 | 四半期振り返り |
| yoro フォロワー数 | `app.bsky.graph.follow` 被フォロー集計 | 社会的信頼指標 |
| メンバー活動指標 | 各 AT Record 件数 | 個人成長ダッシュボード |

---

## Claude Cowork 共通スターターキット (全メンバー共通)

```
# 朝のルーティン (毎日)
「今日のタスクを確認して」
→ listMyFormTasks(status=pending) → 優先度順に表示

# タスク実行
「[タスク名] のフォームを開いて記入を手伝って」
→ kaisya.etzhayyim.com/forms/{form-name} に誘導 + 入力支援

# 完了後
「Step [X] の進捗を yoro に投稿する文章を作って」
→ AT Repo 記録 + yoro フィード更新

# 週次振り返り
「今週の自分の貢献を yoro に週報として投稿して」
→ listMyFormTasks(status=completed, last=7d) 集計 + 投稿下書き

# チーム連携
「[メンバー名] のタスクをリマインドして」
→ sendTeamsMessage でダイレクトリマインド
```

---

## 実装優先順位

### Phase 1 (即時: 今の BPMN 統合に乗る)
- [x] Claude Cowork `listMyFormTasks` — 各メンバーがタスク確認
- [x] `sendBpmnGuidance` — Teams での案内投稿
- [ ] 各メンバーの AT Record 型 (growth contribution) を定義

### Phase 2 (次スプリント: yoro 可視化)
- [ ] yoro Profile ページに `vertex_human_task` 完了件数ウィジェット追加
- [ ] メンバー AT 投稿の成長 KPI 集計 MV (RisingWave)
- [ ] Claude Cowork の「週報自動生成」コマンド

### Phase 3 (月次: 事業成長 KPI ダッシュボード)
- [ ] `com.etzhayyim.apps.kaisya.growthKpi` AT Record + yoro ダッシュボード
- [ ] L9 KPI → yoro 四半期レポート自動投稿
- [ ] 顧客向け yoro Public Profile (専門性ショーケース)

---

## BPMN growth 追加定義

`etzhayyim-yoro-growth-flow.bpmn` — yoro 可視化ゲートを BPMN フローに組み込む

```
[タスク完了 (Step A–L)]
  → [ServiceTask: AI 成果サマリー生成 (Claude Cowork)]
  → [ServiceTask: AT Repo 記録 (kaisya.recordGrowthContribution)]
  → [ServiceTask: yoro フィード更新 (app.bsky.feed.post)]
  → [次ステップ or [END] → L9 成長集計]
```

各 UserTask 完了後に自動で yoro 可視化する derive rule を kotodama.jsonld に追加することで
**「フォームを送信するだけで yoro に実績が積まれる」** 状態を実現する。
