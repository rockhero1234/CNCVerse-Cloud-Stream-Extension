// use an integer for version numbers
version = 1

android {
    buildFeatures {
        buildConfig = true
    }
}

cloudstream {
    // All of these properties are optional, you can safely remove them
    language = "ta"
    description = "Watch Live sports and TV channels"
    authors = listOf("NivinCNC")

    /**
     * Status int as the following:
     * 0: Down
     * 1: Ok
     * 2: Slow
     * 3: Beta only
     * */
    status = 1 // will be 3 if unspecified
    tvTypes = listOf(
        "Live",
    )

    iconUrl = "https://cricfy.pro/wp-content/uploads/2024/11/cropped-cricfytv-2.png"

    isCrossPlatform = true
}
