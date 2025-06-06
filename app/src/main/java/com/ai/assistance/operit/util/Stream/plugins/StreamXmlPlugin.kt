package com.ai.assistance.operit.util.Stream.plugins

import com.ai.assistance.operit.util.Stream.*

private const val GROUP_TAG_NAME = 1
private const val GROUP_CONTENT = 2

/**
 * A stream processing plugin to identify and process XML-formatted data streams. This
 * implementation uses the group capturing mechanism of the StreamKmpGraph.
 */
class StreamXmlPlugin : StreamPlugin {

    override var isProcessing: Boolean = false
        private set

    // This flag is no longer driven by the listener but by the new result object.
    override var isTryingToStart: Boolean = false
        private set

    private var startTagMatcher: StreamKmpGraph
    private var endTagMatcher: StreamKmpGraph? = null
    private var currentTagName: String? = null

    init {
        startTagMatcher =
                StreamKmpGraphBuilder()
                        .build(
                                kmpPattern {
                                    // Group 1: Capture the tag name
                                    group(GROUP_TAG_NAME) {
                                        char('<')
                                        // Tag names cannot start with these chars
                                        noneOf('>', '/', '!', ' ')
                                        // Then, match anything until a space or '>'
                                        greedyStar { noneOf(' ', '>') }
                                    }
                                    // Match attributes until the tag closes
                                    greedyStar { notChar('>') }
                                    char('>')
                                }
                        )

        reset()
    }

    /** Process a single character for XML stream parsing. */
    override fun processChar(c: Char): Boolean {
        // If we are processing content, we are only looking for the end tag.
        endTagMatcher?.let { matcher ->
            val result = matcher.processChar(c)
            if (result is StreamKmpMatchResult.Match && result.isFullMatch) {
                // End tag found, stop processing.
                isProcessing = false
                endTagMatcher = null // Invalidate the end tag matcher.
            }
            return true // Always consume characters while processing.
        }

        // If not processing, look for a start tag.
        val result = startTagMatcher.processChar(c)
        isTryingToStart = result is StreamKmpMatchResult.InProgress

        if (result is StreamKmpMatchResult.Match) {
            // A match occurred, check for our groups.
            result.groups[GROUP_TAG_NAME]?.let {
                // The tag name was captured (e.g., "<tagname").
                // It removes the leading '<'.
                currentTagName = it.substring(1)
            }

            if (result.isFullMatch) {
                // The entire start tag pattern was matched (e.g., "<tagname>").
                isProcessing = true
                isTryingToStart = false
                startTagMatcher.reset()

                // Dynamically build the matcher for the corresponding end tag.
                currentTagName?.let {
                    endTagMatcher =
                            StreamKmpGraphBuilder()
                                    .build(
                                            kmpPattern {
                                                literal("</")
                                                literal(it)
                                                char('>')
                                            }
                                    )
                }
                currentTagName = null // Reset for next tag.
            }
        }
        return isProcessing || isTryingToStart
    }

    /** Initializes the plugin to its default state. */
    override fun initPlugin(): Boolean {
        reset()
        return true
    }

    /** Destroys the plugin. No-op as listener is removed. */
    override fun destroy() {}

    /** Resets the plugin state. */
    override fun reset() {
        isProcessing = false
        isTryingToStart = false
        currentTagName = null
        startTagMatcher.reset()
        endTagMatcher?.reset()
        endTagMatcher = null
    }
}
