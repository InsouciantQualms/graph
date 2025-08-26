# Includes

Please include these files only once per session and apply their guidelines to all subsequent chat requests:

## Internal guidelines applicable to this project

Reference the following guidlines for project definitions [prompts/project.md]

Note, access the above includes relative to this projet root

## External guidelines common to all projects

For Java files [ai/common/src/main/resources/prompts/java.md]
For building and devops [ai/common/src/main/resources/prompts/devops.md]
For documentation [ai/common/src/main/resources/prompts/documentation.md]
For optimizing your output [ai/common/src/main/resources/prompts/optimization.md]
For prompting me [ai/common/src/main/resources/prompts/flags.md]
For proper testing [ai/common/src/main/resources/prompts/testing.md]

Note, to access 'ai' from a relative path, use ../ai

## Missing files

If you are not able to locate or read a requested include file (above), please stop your work and let me know!

# End Includes

# Critical

Never push code to Github
Never modify any files outside of the ~/Dev directory
Do not modify @Stable annotated types (you may instead pause to prompt me before continuing if you want to propose a change)
Summarize changes performed at the end

# Current Instructions

Current instructions for your task(s) can be found at [prompts/current.md].  
Please follow the instructions contained there.

# Additional Instructions

Please scan the project files (using grep or similar) to look for detailed inline code comments.

These take the format:  // CLAUDE {comment_text}
Example:  // CLAUDE Analyze the block of code below
