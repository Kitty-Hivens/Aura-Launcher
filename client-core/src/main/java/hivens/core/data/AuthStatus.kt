package hivens.core.data

import com.google.gson.annotations.SerializedName

enum class AuthStatus { // TODO: В будущем расширить
    @SerializedName("OK")
    OK,
    INTERNAL_ERROR
}
