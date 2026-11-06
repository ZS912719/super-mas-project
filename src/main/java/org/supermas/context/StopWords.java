package org.supermas.context;

import java.util.List;
import java.util.Set;

/**
 * Words that say nothing about a topic: English and French function words, and the phrasing every email shares.
 * A fixed list, written by hand. It does not need to be complete, because words in too many threads are also
 * dropped by frequency.
 */
final class StopWords {
    private StopWords() { }

    static final Set<String> ALL = Set.copyOf(List.of(
            // English function words
            "the", "and", "for", "are", "but", "not", "you", "all", "any", "can", "had", "her", "was", "one", "our",
            "out", "has", "have", "him", "his", "how", "its", "may", "new", "now", "old", "see", "two", "way", "who",
            "did", "get", "got", "let", "say", "she", "too", "use", "that", "with", "this", "from", "they", "will",
            "would", "there", "their", "what", "about", "which", "when", "make", "like", "time", "just", "know",
            "take", "into", "year", "your", "some", "could", "them", "than", "then", "look", "only", "come", "over",
            "think", "also", "back", "after", "work", "first", "well", "even", "want", "because", "these", "give",
            "most", "been", "were", "here", "more", "very", "should", "need", "much", "such", "each", "other", "does",
            "being", "where", "while", "those", "both", "many", "same", "able", "through", "before", "between",
            "under", "again", "once", "during", "without", "within", "upon", "shall", "might", "must", "still",
            "since", "until", "whether", "though", "however", "yet", "per", "via", "etc", "yes", "okay", "sure",
            "going", "done", "made", "said", "today", "tomorrow", "yesterday", "week", "next", "last", "day", "days",
            "also", "already", "really", "something", "anything", "nothing", "everything", "someone", "anyone",
            "thing", "things", "let", "lets", "please", "thank", "thanks", "thx", "regards", "best", "kind", "dear",
            "hello", "hey", "sincerely", "cheers", "hope", "following", "attached", "attachment", "below", "above",
            "email", "mail", "message", "messages", "original", "forwarded", "sent", "subject", "wrote", "reply",
            "call", "questions", "question", "let", "know", "info", "information", "fyi", "asap", "re", "fw", "fwd",
            "monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday",
            "january", "february", "march", "april", "june", "july", "august", "september", "october", "november",
            "december", "com", "www", "http", "https", "html", "org", "net", "edu",
            // French function words and phrasing
            "les", "des", "une", "est", "pas", "que", "qui", "dans", "pour", "par", "sur", "avec", "son", "ses",
            "aux", "mais", "ou", "donc", "car", "nous", "vous", "ils", "elles", "elle", "leur", "leurs", "votre",
            "vos", "notre", "nos", "mon", "mes", "ton", "tes", "sont", "être", "avoir", "fait", "faire", "plus",
            "moins", "tout", "tous", "toute", "toutes", "cette", "ces", "cet", "comme", "aussi", "bien", "très",
            "peut", "peux", "pouvez", "merci", "bonjour", "bonsoir", "cordialement", "salutations", "madame",
            "monsieur", "objet", "envoyé", "ci", "joint", "jointe", "lundi", "mardi", "mercredi", "jeudi",
            "vendredi", "samedi", "dimanche", "janvier", "février", "mars", "avril", "mai", "juin", "juillet",
            "août", "septembre", "octobre", "novembre", "décembre", "encore", "déjà", "alors", "après", "avant",
            "entre", "sans", "sous", "chez", "dont", "quand", "comment", "pourquoi", "été", "était", "suis",
            "sommes", "êtes", "avez", "avons", "ont", "ainsi", "cela", "ceci", "celui", "celle", "lui", "même"));
}
