# etzhayyim 業務設計 — 逆トポロジーソート計画

作成日: 2026-04-20
対象: etzhayyim 契約メンバー全員

---

## メンバー × スキルマッピング

| ハンドル | 役職 | 主スキル | BPMN タスク |
|---|---|---|---|
| j.kawasaki | CEO | 戦略/経営判断/最終承認 | A (戦略方針), L (最終承認) |
| a.nakamura | COO | 案件受付/組織運営/納品確認 | B (案件受付), D (ルーティング), K (納品確認) |
| k.bakshi | CLO | 法務/訴訟/契約締結 | C (契約締結), J (法務最終確認) |
| n.takahashi | Cybersecurity事業部責任者 | 脅威分析/セキュリティ評価 | E (Cyber評価) |
| t.chikada | CS部 | セキュリティエンジニアリング/インフラ | H (技術要件) |
| f.tanaka | エンジニア | 開発実装/backend | I (開発実装) |
| y.nishino | エンジニア/CLO tier support | 開発実装/法務補佐 | I (開発実装), CLO補佐 |
| t.ichihara | Branding事業部責任者 | ブランド戦略/PR | F (ブランド戦略) |
| k.takahashi | クリエイティブディレクター | ビジュアルデザイン/制作 | G (Creative方向性) |

---

## 逆トポロジーソート (Reverse Topological Sort)

目標からさかのぼって依存関係を展開した順序:

```
Level 9 (ゴール)
└── L: CEO最終承認 [j.kawasaki] ← form: final-approval
    └── depends on: K

Level 8
└── K: COO 納品確認 [a.nakamura] ← form: delivery-confirm
    └── depends on: J

Level 7
└── J: CLO 法務最終確認 [k.bakshi] ← form: contract-review
    └── depends on: E∧G∧I (all parallel)

Level 6 (並列)
├── E: Cybersecurity評価 [n.takahashi] ← form: security-assessment
│   └── depends on: D
├── G: クリエイティブ方向性 [k.takahashi] ← form: creative-brief
│   └── depends on: F
└── I: 開発実装 [f.tanaka / y.nishino] ← form: delivery-confirm
    └── depends on: H

Level 5
├── F: ブランド戦略 [t.ichihara] ← form: creative-brief
│   └── depends on: D
└── H: 技術要件 [t.chikada] ← form: security-assessment
    └── depends on: D

Level 4
└── D: 案件ルーティング [DMN: dmn-task-routing] ← BusinessRuleTask
    └── depends on: C

Level 3
└── C: 契約締結 [k.bakshi] ← form: contract-review
    └── depends on: B

Level 2
└── B: 案件受付 [a.nakamura] ← form: project-intake
    └── depends on: A

Level 1 (起点)
└── A: CEO 戦略方針 [j.kawasaki] ← form: project-intake
    └── depends on: START
```

---

## Form → BPMN タスク マッピング

| Form ファイル | 対応 BPMN タスク | 担当者 | vertex_form_task.type |
|---|---|---|---|
| `project-intake.form.json` | A (戦略方針), B (案件受付) | j.kawasaki / a.nakamura | `intake` |
| `contract-review.form.json` | C (契約締結), J (法務最終確認) | k.bakshi | `contract-review`, `legal-final-review` |
| `security-assessment.form.json` | E (Cyber評価), H (技術要件) | n.takahashi / t.chikada | `security-assessment`, `tech-requirements` |
| `creative-brief.form.json` | F (ブランド戦略), G (Creative) | t.ichihara / k.takahashi | `branding-strategy`, `creative-direction` |
| `delivery-confirm.form.json` | I (開発), K (納品確認) | f.tanaka+y.nishino / a.nakamura | `development`, `delivery-confirm` |
| `final-approval.form.json` | L (CEO最終承認) | j.kawasaki | `final-approval` |
| `legal-case.form.json` | 法務案件管理フロー専用 | k.bakshi | `litigation`, `pre-litigation` |

---

## DMN 判定フロー

```
案件入力 (project-intake.form.json の checkbox群)
    ↓
dmn-task-routing (COLLECT/LIST hitPolicy)
    ├── hasLegalRisk=true      → clo (k.bakshi) assigned
    ├── hasCyberRisk=true      → cybersecurity (n.takahashi) assigned
    ├── hasBrandingNeed=true   → branding (t.ichihara) assigned
    └── hasTechDev=true        → engineer (f.tanaka) assigned

dmn-risk-assessment (FIRST hitPolicy)
    ├── caseType=litigation    → riskLevel=high, deadline=3d, approval=ceo+clo
    ├── estimatedAmount≥1000   → riskLevel=high, deadline=5d, approval=ceo+clo
    ├── hasExternalCounsel     → riskLevel=high, deadline=7d, approval=ceo+clo
    └── default                → riskLevel=low, deadline=30d, approval=coo
```

---

## hc.etzhayyim.com 統合

各 UserTask は `hc_worker` プロパティを持ち、`hc.etzhayyim.com` の以下テーブルに反映:

| BPMN Task | hc テーブル | レコード |
|---|---|---|
| A-L: 全 UserTask | `hc_tasks` | task_id, assignee, status, due_date |
| 各タスク完了 | `hc_assignments` | worker, task_ref, completed_at |
| 法務案件 | `hc_compliance_log` | case_id, action, actor, timestamp |

hc.etzhayyim.com の MCP ツール `etzhayyim.hc.assignTask` を ServiceTask で呼び出し、シフト管理 (`hc_shifts`) と連動。

---

## kaisya ポータル統合

kaisya.etzhayyim.com の `vertex_form_task` テーブルが各フォーム送信を受け取る:

```sql
INSERT INTO vertex_form_task (
  form_task_id, form_type, assignee_did, project_ref,
  status, submitted_at, payload_json
) VALUES (
  gen_random_uuid()::text,
  $form_type,         -- 'intake'|'contract-review'|'security-assessment'|...
  $assignee_did,      -- 'did:web:kaisya.etzhayyim.com'
  $project_ref,       -- 案件ID
  'pending',
  now(),
  $payload::jsonb
);
```

kaisya Bot (Teams: GJ/CE/General) が pending タスクを自動通知。

---

## 業務進行のトリガー条件

フォームを提出するだけで以下が自動進行:

1. **project-intake 送信** → BPMN instance 起動 → Teams 受付通知
2. **contract-review 送信** → DMN ルーティング実行 → 並列タスク割当
3. **security-assessment 送信** (E完了) → ParallelGateway Join 待機
4. **creative-brief 送信** (G完了) → ParallelGateway Join 待機
5. **delivery-confirm 送信** (I完了) → ParallelGateway Join → J (CLO) 起動
6. **contract-review 送信** (J完了) → K (COO) 起動
7. **delivery-confirm 送信** (K完了) → L (CEO) 起動
8. **final-approval 送信** → 案件完了 + Teams 完了通知

**人間がすべき操作: フォームを開いて入力し送信するだけ。**
