# Excerpt Relevance (benchmark plan C11)

Run 2026-11-11T11:12Z. 22 reply-expected synthetic emails × 6 knowledge documents = 132 pairs. Relevant: the document holds a gold fact of the email (`mustMention`, `mustAsk`); noise: it holds a term of the email's `noise` check. The filter sees all extracted requests and the subject (an upper bound of one specialist's view). Knowledge documents are in English and the filter keeps excerpts in another language than the requests, so it can hardly judge French emails.

| Email language | Relevant pairs | Relevant kept (recall) | Other pairs | Other dropped | Noise pairs | Noise dropped |
| --- | --- | --- | --- | --- | --- | --- |
| English | 12 | 11 / 12 (0.92) | 90 | 71 / 90 (0.79) | 25 | 21 / 25 (0.84) |
| French | 4 | 4 / 4 (1.00) | 26 | 6 / 26 (0.23) | 2 | 0 / 2 (0.00) |
