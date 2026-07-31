# CLAUDE.md — kaisya

会社ポータル。事務所（`cloud-itonami/lawfirm`）が抱えているものを、会社側から見る画面。
概要は [`README.md`](README.md)。

## 触る前に読むもの

1. [`src/kaisya/contract.cljc`](src/kaisya/contract.cljc) の docstring —
   **なぜこのポータルが practice に依存せず、何も計算しないのか。**
   このリポジトリの仕様はここにある。
2. [`src/kaisya/console.cljc`](src/kaisya/console.cljc) の docstring —
   要対応が事件一覧より前にある理由と、並び順の根拠。
3. skill `kotoba-uiux` と `orgs/kotoba-lang/kotoba-ui/docs/agent-guide.md` —
   UI を1行でも書く前に。

## 開発

```bash
clojure -M:test              # 25 tests / 56 assertions
clojure -M:lint              # clj-kondo, errors fail, warnings 0 を維持する
clojure -M:emit-processes    # bpmn/*.bpmn -> resources/kaisya/processes.edn
clojure -M:render-console    # docs/samples/kaisya-console.html を再生成
```

- ポータルは design-quality の決定論的 HIG/WCAG 監査で **100.00** を維持する
  （`console_test.clj` の `score-floor`）。**回帰を通すために floor を下げない。**
- `render-console` は **byte-identical across reruns** でなければならない
  （`rendering-is-deterministic`）。時刻・乱数を入れない。
- console を変えたら `clojure -M:render-console` を回す
  （`the-checked-in-sample-matches-what-the-code-renders` が落ちる）。

## この repo 固有の不変条件（破らない）

- **ポータルは数字を計算しない。** 集計・平均・残数を `kaisya` 側で導出しない。
  すべて summary に入ってくる。事務所コンソールと食い違ったとき、役員が見るのは
  こちらなので、**間違っている方が信じられる**。
- **`lawfirm` への実行時依存を作らない。** `deps.edn` の `:deps` に足さない。
  テスト時だけの依存で、契約が本物の producer を満たすことの検証にのみ使う。
- **欠落を 0 として描かない。** `contract/problems` が名指しし、`console/n-or-gap`
  が `—` を返す。`(or x 0)` を metric に入れない。
- **承認の操作をポータルに置かない。** 承認は弁護士本人の行為で、事務所コンソール側。
  `:approve` を出す `ui/button` を足さない（`console_test` が検出する）。
- **`resources/kaisya/processes.edn` を手編集しない。** 生成物。
  `bpmn_test.clj` の `processes-are-current` が抽出をやり直して突き合わせる。
- **UI は `kotoba-ui.core` + `appkit.core` のみ require。** 生の hex はテーマ map の
  2 色だけ（`console_test` が `src/kaisya/console.cljc` 全体を走査して検証する）。
  状態色は system palette トークンを使う。
- **`bb.edn` / `.sh` を新規に置かない**（ADR-2607173000、workspace CLAUDE.md）。
  スクリプトが要るなら nbb か、この repo の慣習に合わせて `clojure -M:<alias>`。

## テストの約束

- `kaisya.demo` はテストとデモページの**共通**の ground truth。
  デモ専用のフィクスチャを別に作らない。
- `kaisya.demo/summary` は**3事件**で、1件が徒過・1件が回復可能な問題・1件が clean。
  1事件に減らさない——並び順のテストが検証するものが無くなる。
- 契約に項目を足したら、`contract_test.clj` に「欠けていると problems に出る」と
  「揃っていれば valid?」の両方を書く。片方だけだと、常に problem を返す実装でも緑になる。

## legacy — `bpmn/`

`etzhayyim/root` の `60-apps/etzhayyim-project-kaisya` から抽出した BPMN
（出自は [`migration.edn`](migration.edn)）。**実行系ではない。**
ポータルはプロセス名とステップ名を表示するだけで、engine は持たない。
Camunda の `camunda:*` 属性（decisionRef / resultVariable 等）は抽出していない。
