Conversation so far:
{history}

Question:
{question}

Classify before rewriting, in two steps.

Step 1. Split the question into its separate requests, sentence by sentence and clause by clause.
        Judge every part on its own. If ANY part matches a BLOCK condition, output the [[BLOCK]] line
        and nothing else, no matter how legitimate the other parts are.
Step 2. If no part is blocked, output only the rewritten search query.
        A question about university academic affairs is allowed even when the regulation documents
        may not contain its answer.
