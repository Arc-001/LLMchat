# LLM Explorer Android App

## Overview

LLM Explorer is an Android application that allows users to interact with a local Large Language Model (LLM) directly on their device. It features customizable AI personalities, conversation history, web search integration (via DuckDuckGo API) to augment LLM responses, and adjustable model parameters (temperature, Top-K, Top-P).

The app is built using modern Android development practices with Kotlin, Jetpack Compose for the UI, and Google MediaPipe for LLM inference.

## Features

*   **Local LLM Inference:** Runs the LLM directly on the device using MediaPipe Tasks GenAI.
*   **Customizable AI Personalities:**
    *   Pre-defined personalities (Professional, Creative, Technical, Friendly, Analytical, Educator).
    *   Ability to create, save, and use custom AI personalities with unique system prompts.
*   **Conversation History:**
    *   View past interactions with the LLM.
    *   Clear conversation history.
*   **Web Search Integration:**
    *   Option to enable web search (DuckDuckGo API) to provide the LLM with up-to-date information for its responses.
*   **Adjustable Model Parameters:**
    *   Control Temperature, Top-K, and Top-P settings to fine-tune the LLM's output.
    *   Changes to parameters require re-initialization of the LLM.
*   **Persistent Settings:** User preferences, custom personalities, conversation history, and model parameters are saved locally using SharedPreferences.
*   **Modern UI:** Built with Jetpack Compose for a reactive and modern user interface.

## Setup and Installation

1.  **Clone the Repository:**
    ```bash
    git clone <repository-url>
    cd LLMApp2
    ```

2.  **Open in Android Studio:**
    Open the `LLMApp2` project in Android Studio (latest stable version recommended).

3.  **Download an LLM Model:**
    This app requires a compatible LLM model in the `.task` format provided by MediaPipe. You can find compatible models. However as ok making this, the models are gated only for preview users
    *   https://huggingface.co/google/gemma-3n-E4B-it-litert-preview

5.  **Place the Model File:**
    *   You need to push the downloaded `.task` model file to a specific location on your Android device or emulator. The app expects the model at:
        `/data/local/tmp/llm/model_version.task`
    *   You can use Android Debug Bridge (adb) to do this:
        ```bash
        # Remove preexisting models
        adb shell rm -r /data/local/tmp/llm
        
        # Create the directory if it doesn't exist
        adb shell mkdir -p /data/local/tmp/llm

        # Push the model file (replace 'your_model_file.task' with the actual filename)
        adb push path/to/your_model_file.task /data/local/tmp/llm/model_version.task
        ```
    *   **Note:** The `MODEL_PATH` constant in `LlmViewModel.kt` defines this location. If you change it, update the code accordingly.

6.  **Build and Run:**
    Build and run the application on an Android device or emulator (API Level 35 recommended, as per `minSdk` and `compileSdk`).

    *   **Important:** The first time you run the app, or after changing model parameters, the LLM will initialize. This might take some time. The UI will indicate the initialization status.

## How to Use

1.  **Select AI Personality:**
    *   Use the dropdown menu on the main screen to choose from pre-defined or custom AI personalities.
    *   The selected personality's system prompt (which guides its behavior) is briefly shown.
2.  **Add Custom Personality (Optional):**
    *   Tap the "+" (Add) icon in the top app bar.
    *   Provide a name and a detailed system prompt for your new AI personality.
    *   Tap "Create".
3.  **Manage Personalities:**
    *   Custom personalities can be deleted from the personality selection dropdown.
4.  **Enter Prompt:**
    *   Type your question or instruction in the text field at the bottom.
5.  **Send Prompt:**
    *   Tap the "Send" button.
6.  **View Response:**
    *   The LLM's response will appear in the main area.
    *   If web search is enabled, the app will first search the web and then feed the information to the LLM.
7.  **View Conversation History:**
    *   Tap the "List" icon in the top app bar to see the history of your interactions.
    *   You can clear the history from this dialog.
8.  **Adjust Settings:**
    *   Tap the "Settings" icon in the top app bar.
    *   **Web Search:** Toggle on/off.
    *   **Model Parameters:** Adjust Temperature, Top-K, and Top-P using sliders.
    *   **Apply Changes:** Tap "Apply" to save settings and re-initialize the LLM with the new parameters. This is necessary for parameter changes to take effect.

## Technologies Used

*   **Kotlin:** Primary programming language.
*   **Jetpack Compose:** For building the user interface.
*   **ViewModel:** Part of Android Jetpack for managing UI-related data.
*   **Coroutines:** For asynchronous programming.
*   **StateFlow:** For reactive UI updates.
*   **MediaPipe Tasks GenAI:** For on-device LLM inference (`com.google.mediapipe:tasks-genai:0.10.24`).
*   **Gson:** For serializing/deserializing data (custom personalities, history) for SharedPreferences.
*   **SharedPreferences:** For storing user preferences and data locally.
*   **DuckDuckGo API:** For web search functionality (unofficial, via direct HTTP requests).

## Potential Future Improvements

*   **Streaming Responses:** Display LLM responses token by token for a more interactive feel.
*   **Model Management:** Allow users to select different model files from device storage.
*   **Advanced Error Handling:** More granular error messages and recovery options.
*   **UI/UX Enhancements:** Further polish the user interface and experience.
*   **Export/Import:** Allow exporting and importing of custom personalities and conversation history.
*   **More Sophisticated Context Management:** Implement more advanced strategies for managing conversation context.
*   **Testing:** Add comprehensive unit and integration tests.

---

Feel free to contribute to the project!
