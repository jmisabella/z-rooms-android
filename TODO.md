# TODO

Read CONTEXT.md to understand this project from a high-level. Now, we have the TTS Story/Poetry mode text content in the app/src/main/assets/tts_content/ folder: Each story has a subdirectory in TTSContent with the story's name, and the story's prose is inside the Stories/ subdirectory as chapters

### March 9, 2026 - NEW

##### SESSION 1
- The TTS functionality of the corresponding iOS app currently literally reads "asterisk" if it comes across asterisks in the text, which is not ideal. We don't know whether this Android version has the same bug, but nevertheless we don't want that behavior in this project either. Many works include a single or multiple asterisks to break a chapter into multiple parts. We don't want TTS to literally say "asterisk" or "asterisks" when it encounters these. Similarly, it should not literally read "dash" or "dashes" if it comes across sole dashes, and should not literaly read "underscores" if it comes across sole underscores. 

##### SESSION 2
- The final revised Aphelion story and its corresponding poems exists at /Users/jeffrey/Documents/Stories/Aphelion/Aphelion_R9.txt. This final revised draft contains all chapters and poems in a single document. We need to update this project's story chapters and poems for Aphelion to match this final revised draft. This project's files are in app/src/main/assets/tts_content/aphelion/, with poems in Poems/ and chapters in Stories/. Notice that many of the poems were removed and that chapters may have been removed or added. We need our text files for this project to be updated accordingly. Notice that the chapters apparently must start like 01_chapter... with the 2-digit numeric prefix. 

- The final revised The Eighteen Paradox story and its corresponding poems exists at /Users/jeffrey/Documents/Stories/The_Eighteen_Paradox/The_Eighteen_Paradox_R7.txt. This final revised draft contains all chapters and poems in a single document. We need to update this project's story chapters and poems for Aphelion to match this final revised draft. This project's files are in app/src/main/assets/tts_content/the_eighteen_paradox/, with poems in Poems/ and chapters in Stories/. Notice that many of the poems were removed and that chapters may have been removed or added. We need our text files for this project to be updated accordingly. Notice that the chapters apparently must start like 01_chapter... with the 2-digit numeric prefix. 

##### SESSION 3
- We no longer want the story Soil (app/src/main/assets/tts_content/soil/) to exist in this project, as it's not yet ready for primetime. Please remove this story altogether from this project. After the changes, you may need to follow the document ADDING_NEW_STORIES.md in order for the updated stories to be made available from the App. 
