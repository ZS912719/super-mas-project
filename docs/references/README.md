# References

| File | What |
| --- | --- |
| [`multi_agent_project_en.pdf`](multi_agent_project_en.pdf) | The project statement: *Design of a multi-agent system for automatic email responses using local LLMs*, M2 Artificial Intelligence, Multi-Agent Systems course. Checked section by section in [`docs/compliance.md`](../compliance.md) |

## Bibliography of the targeted architecture

The PDFs are publisher copies, kept locally in `bibliography/` and ignored by Git for copyright reasons. Obtain them from the publishers:

- Hayes-Roth, B. (1985). *A Blackboard Architecture for Control.* Artificial Intelligence, 26(3), 251-321. https://doi.org/10.1016/0004-3702(85)90063-3 — local file `bibliography/hayes-roth-1985-blackboard-architecture-for-control.pdf`. The model for the controller (`control.AgendaStrategy`): domain and control blackboards, knowledge-source activations, a scheduler rating them against a control plan.
- Corkill, D. D. (2003). *Collaborating Software: Blackboard and Multi-Agent Systems & the Future.* Proceedings of the International Lisp Conference 2003. — local file `bibliography/corkill-2003-collaborating-software.pdf`. The split between knowledge-source estimates and generic control, and the blackboard as community memory.

How the prototype follows them: [`docs/plans/blackboard-control.md`](../plans/blackboard-control.md), [`docs/compliance.md` §5](../compliance.md#5-targeted-architecture-bibliography).
