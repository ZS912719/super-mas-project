# Prompt variants

Composer prompts for ablations of the reply-quality benchmark (`--composer-prompt FILE`, benchmark plan W3). Each replaces only `composer.txt`; `common.txt` (trust boundary, owner and sender, no promises) and the contributing roles' rules stay, so a variant changes the reply instructions and nothing that guards safety.

| File | Change against the bundled `composer.txt` | Question |
| --- | --- | --- |
| `composer-without-use-only-the-facts.txt` | Without the sentence "Use only the facts that help answer a request" | Does that rule make replies terser (step 13)? |
| `composer-neutral.txt` | One neutral sentence instead of the composer's instructions | Influence of the system prompt (to-do 3.3) |
