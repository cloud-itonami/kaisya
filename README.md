# kaisya — 会社ポータルと会社記録

## 会社記録（`kaisya.itonami.app`）

`kaisya.itonami.app` の入口は `public/operations.html` から配信する
Gftd Japan の空の会社環境です。会計仕訳、勤怠打刻、
従業員・資産・債務・文書・賃金台帳を、オーナーのパスキーで保護された D1 に記録します。
実データの移行は行いません。登録されたパスキーは管理者が本人を確認してから
オーナーに昇格し、それまでは会社記録を閲覧できません。

- `src/kaisya/operations.cljk`: 記帳・打刻・台帳の純粋な状態遷移
- `src/kaisya/edge/http.cljk`: パスキー、所有者セッション、記録 API
- `src/kaisya/edge/store.cljk`: D1 の版番号付き保存
- `src/kaisya/operations_page.cljk`: 公開画面の生成
- `src/kaisya/browser_app.cljs`: パスキーと会社記録の操作

```bash
npm ci
npm run build
npx wrangler d1 migrations apply cloud-itonami-kaisya-operations --remote
npx wrangler deploy --dry-run
npx wrangler deploy
```

この初期版は Money Forward のデータを自動移行・同期しません。銀行連携、
給与計算、税申告、法定保存要件への適合性は未実装です。実務の全面切り替えには
各機能の検証と既存記録の照合が必要です。

---

## 既存の会社ポータル

事務所が**何を抱えていて、何が既に問題になっているか**を示す会社側の画面。
`cloud-itonami/lawfirm` の事務所コンソールが「この事件で次に何をするか」に答えるのに対し、
こちらは「回復できない失敗がどこにあるか」に答える。

**成熟度: `:implemented`.** 31 tests / 91 assertions green（`kbb -M:test`）、
`kbb -M:lint` warnings 0、レンダリング済みポータルは
[design-quality](https://github.com/kotoba-lang/design-quality) の決定論的
HIG/WCAG 監査で **100.00 / 100**。

- 生成済みポータル: [`docs/samples/kaisya-console.html`](docs/samples/kaisya-console.html)
- Organization セットアップ状態: [`docs/samples/kaisya-setup.html`](docs/samples/kaisya-setup.html)
- 旧ポータルの公開入口: [`public/index.html`](public/index.html) → `kaisya.itonami.cloud`
- 会社記録の公開入口: [`public/operations.html`](public/operations.html) → `kaisya.itonami.app`

従来の `kaisya.itonami.cloud` 向け画面は、未設定時は Organization →
ドメイン確認 → メンバー → 仕事道具の順に案内し、完了後は会社ポータルを表示する。
ドメイン確認の authority は `cloud-itonami-app` が持ち、この repo は発行済みの
TXT challenge と確認状態を描画するだけ。Domain Connect は DNS provider の自動設定を
足す任意レイヤーであり、手動 TXT 確認を置き換える信頼根ではない。
旧セットアップ画面は会社ドメインを resident app の
`http://localhost:1338/#settings` へ渡す。新しい会社記録は
`kaisya.itonami.app` のパスキーを持ち、旧ポータルの projection とは別の境界です。

---

## 一段落でいうと

このポータルは**自分では何も計算しない**。数字はすべて
`lawfirm.projection/practice-summary` が事務所側のゲートと同じ関数から算出したものが
渡ってくる。ここで再計算すれば、いずれ事務所コンソールと食い違い、しかも
**役員が見るのはこちらなので、間違っている方が信じられる**。

だから kaisya は「誰かが計算した投影」を描画するだけで、数字の意味は一切所有しない。
この形（`kaisya.contract`）を出せる practice actor なら何でも描画できる。

---

## 何が入っているか

| namespace | 役割 |
|---|---|
| [`kaisya.contract`](src/kaisya/contract.cljk) | practice が渡すべき形と、渡されなかったときに何が起きるか。`action-required` の並び順（回復不能なものが先）もここ |
| [`kaisya.console`](src/kaisya/console.cljk) | ポータル本体（kotoba-ui、pure `.cljc` hiccup、SSR） |
| [`kaisya.bpmn`](src/kaisya/bpmn.cljk) | `bpmn/*.bpmn` から業務プロセス定義を**生成**する（手で書き写さない） |
| [`kaisya.demo`](src/kaisya/demo.cljk) | サンプル会社。テストとデモページが同じ記録を使う |

```bash
kbb -M:test              # 31 tests / 91 assertions
kbb -M:lint              # clj-kondo, errors fail
kbb -M:emit-processes    # bpmn/*.bpmn -> resources/kaisya/processes.edn
kbb -M:render-console    # docs/samples/kaisya-console.html を再生成
```

---

## 欠落は 0 ではない

`contract/problems` は**渡されなかった値を名指しする**。ポータルが
「徒過 0」と表示したのが「実際に 0 だった」からなのか「誰もその欄を埋めなかった」
からなのか区別できないなら、それは記録が与えていない保証を画面が与えていることになる。

だから欠落があるときは、数字より**上**に欠落パネルが出る（下に出したら読者は
数字を読んでから欠落に気づく）。欠けている metric は `0` ではなく `—` で描画される。

同じ判断は `lawfirm.console`（表示専用の計算をしない）と
`cloud-itonami-app`（残高不明を ¥0 と描かない）にもある。

---

## 要対応の並び順

件数順ではなく、**失敗の回復不能さ順**に並べる。

| 順 | 区分 | なぜこの位置か |
|---|---|---|
| 1 | 期限徒過 | 後から回復できない |
| 2 | 送達先の再確認 | 電話1本。ただし放置すると誤送信になる |
| 3 | 回答の精査待ち | 名前のついた弁護士の机の上で止まっている |
| 4 | 期限間近 | まだ間に合う |
| 5 | 送達結果の未確認 | 追いかければ分かる |

---

## このポータルが持たない機能

- **承認ボタンを持たない。** 承認は弁護士本人の行為で、事務所コンソール側で行う。
  ここに置くと「ポータルで承認できる」という誤った可能性を画面が示唆する。
- **practice への実行時依存を持たない。** `lawfirm` は**テスト時だけ**の依存で、
  「`practice-summary` が実際に出す値が `kaisya.contract` を満たすか」を検証するために使う
  （`contract_test.clj` の `lawfirms-real-output-satisfies-the-contract`）。
  誰も走らせない契約はコメントなので、契約の側で1本だけ本物に当てている。

---

## 業務プロセスは BPMN から生成する

`bpmn/` の BPMN は `etzhayyim/root` から引き継いだ、この会社の業務フローの記録
（出自は [`migration.edn`](migration.edn)）。ポータルはそれを**名前として表示する**だけで、
実行はしない。

手で書き写さないのは、写した一覧が2つ目の真実になって、図を編集した瞬間に静かにずれ、
**どちらももっともらしく見えるので気づけない**から。`bpmn_test.clj` の
`processes-are-current` が抽出をやり直して差分があれば落ちる。

現在: 3 プロセス / 58 ステップ（法務案件管理・メイン業務フロー・yoro growth flow）。

---

## UI 規約（skill `kotoba-uiux` / ADR-2607122200）

- require は `kotoba-ui.core` + `appkit.core` のみ
- 生の hex は theme map の 2 色だけ（`console_test.clj` が全ソースを走査して検証）
- 状態色は system palette トークン（`var(--hig-palette-red)` 等）
- app CSS は 2 ルール・unlayered（`@layer` と詳細度勝負をしない）
- レイアウトは shell の scaffold から。`.layout` / `.hero` を手書きしない
- タイポグラフィは HIG の 11 text style のみ

---

## 既知の限界

1. **旧ポータルには HTTP の入口が無い。** サマリと承認待ちキューを渡すのはホストの仕事。
   Kotoba には現時点で ingress capability が無いため（CLAUDE.md）、
   エントリポイントは cljs 側の責務。
2. **`:act` ボタンは何もしない。** SSR の意味論しか持たない。
3. **BPMN は名前だけ。** フロー・ゲートウェイ・条件は抽出していない。
   本当に構造が要るようになったら、正規表現ではなく XML パーサに置き換える
   （`kaisya.bpmn` の docstring に理由を書いてある）。
4. **プロセスと practice は繋がっていない。** 「法務案件管理」の各ステップが
   lawfirm のどの op に対応するかは、まだどこにも書かれていない。
