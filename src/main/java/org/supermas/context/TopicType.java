package org.supermas.context;

/** The kinds of topic. An email can belong to several at once. */
public enum TopicType {
    /** A declared subject, such as a course or a project. */
    SUBJECT,
    /** The email's own thread, in its exact project. */
    DISCUSSION,
    /** The verified sender's other threads, in the same project only. Off by default. */
    CORRESPONDENT
}
