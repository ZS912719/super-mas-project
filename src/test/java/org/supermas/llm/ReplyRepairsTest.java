package org.supermas.llm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import org.junit.jupiter.api.Test;

/** The two repairs, on outputs the 8 B model produced in the main runs (B59, B61). */
class ReplyRepairsTest {
    private static final String STUDENT_DATA = "<<EMAIL ADDRESSES>>\nFrom (the sender, who receives the reply): Sarah Klein <sarah.klein@students.example.edu>\n"
            + "To (the recipient, on whose behalf the reply is written): e.martin@example.edu\n<<EMAIL SUBJECT>>\nExtension\n";
    private static final String STUDENT_EMAIL = "Dear Prof. Martin,\n\nI have been ill this week. Could I have an extension?";

    @Test
    void aReplyAfterCopiedHeaderBlocksIsKept() {
        String output = "<<EMAIL ADDRESSES>>\nFrom: n.bernard@example.fr\nTo: h.lefevre@labo.example.org\n\nBonjour,\n\n"
                + "Jeudi me convient pour la réunion. Je lirai le brouillon avant et je t'enverrai mes remarques.\n\nBest regards,";
        assertEquals(Optional.of("Bonjour,\n\nJeudi me convient pour la réunion. Je lirai le brouillon avant et je t'enverrai mes remarques.\n\nBest regards,"),
                ReplyRepairs.withoutEchoedHeader(output, "Bonjour Nadia, le brouillon de l'article est en pièce jointe. Pouvons-nous nous réunir jeudi ?"));
    }

    @Test
    void copiedHeadersFollowedByTheEmailItselfOrMoreBlocksAreStillRefused() {
        String email = "Bonjour,\n\nLa bibliothèque sera fermée ce week-end pour maintenance. Les horaires habituels reprendront ensuite.";
        String output = "<<EMAIL ADDRESSES>>\nFrom: Madame la bibliothécaire\nTo: y.benali@etu.example.fr\n<<EMAIL SUBJECT>>\nHoraires d'ouverture\n"
                + email + "\n\nBest regards,";
        assertEquals(Optional.empty(), ReplyRepairs.withoutEchoedHeader(output, email), "the email repeated is not a reply");
        assertEquals(Optional.empty(), ReplyRepairs.withoutEchoedHeader("<<EMAIL ADDRESSES>>\nFrom: a\n<<REPLY PLAN (JSON)>>\n{}", email));
        assertEquals(Optional.empty(), ReplyRepairs.withoutEchoedHeader("Dear Sarah, see <<EMAIL TEXT>>", email), "only a leading header");
    }

    @Test
    void aGreetingToTheEmailsAddresseeIsChangedToTheSender() {
        Optional<ReplyRepairs.Fixed> fixed = ReplyRepairs.withSendersGreeting("Dear Prof. Martin,\n\nThe course leader decides.\n\nBest regards,",
                STUDENT_DATA, STUDENT_EMAIL);
        assertEquals("Dear Sarah Klein,\n\nThe course leader decides.\n\nBest regards,", fixed.orElseThrow().body());
        assertTrue(fixed.get().note().contains("Prof. Martin"));
        assertEquals("Dear Sarah Klein,", ReplyRepairs.withSendersGreeting("Dear Martin,\n\nNoted.", STUDENT_DATA, STUDENT_EMAIL)
                .orElseThrow().body().lines().findFirst().orElseThrow(), "the recipient's address names Martin too");
    }

    @Test
    void theRightGreetingAndUnclearCasesAreLeftAlone() {
        assertEquals(Optional.empty(), ReplyRepairs.withSendersGreeting("Dear Sarah,\n\nNoted.", STUDENT_DATA, STUDENT_EMAIL));
        assertEquals(Optional.empty(), ReplyRepairs.withSendersGreeting("Dear Pierre,\n\nNoted.", STUDENT_DATA, STUDENT_EMAIL),
                "a name that is neither the sender nor an addressee is not guessed at");
        assertEquals(Optional.empty(), ReplyRepairs.withSendersGreeting("Hello,\n\nNoted.", STUDENT_DATA, STUDENT_EMAIL));
    }

    @Test
    void frenchTitleGreetingsAndSendersWithoutANameGetAPlainGreeting() {
        String data = "From (the sender, who receives the reply): Lucas Moreau <lucas.moreau@etu.example.fr>\n"
                + "To (the recipient, on whose behalf the reply is written): c.dubois@example.fr\n";
        assertEquals("Bonjour Lucas Moreau,", ReplyRepairs.withSendersGreeting("Madame Dubois,\n\nC'est noté.", data,
                "Bonjour Madame Dubois,\n\nJ'ai été malade.").orElseThrow().body().lines().findFirst().orElseThrow());
        String bare = "From (the sender, who receives the reply): o.haddad@students.example.edu\n"
                + "To (the recipient, on whose behalf the reply is written): e.martin@example.edu\n";
        assertEquals("Hello,", ReplyRepairs.withSendersGreeting("Dear Prof. Martin,\n\nNoted.", bare, STUDENT_EMAIL)
                .orElseThrow().body().lines().findFirst().orElseThrow());
    }
}
