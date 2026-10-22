package mx.lumo.auto.car

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.SearchTemplate
import androidx.car.app.model.Template

/** Teclado del auto: busca en YouTube o abre un enlace. */
class SearchScreen(carContext: CarContext) : Screen(carContext) {

    override fun onGetTemplate(): Template =
        SearchTemplate.Builder(object : SearchTemplate.SearchCallback {
            override fun onSearchTextChanged(searchText: String) = Unit

            override fun onSearchSubmitted(searchText: String) {
                if (searchText.isNotBlank()) CarBrowser.search(carContext, searchText)
                screenManager.pop()
            }
        })
            .setHeaderAction(Action.BACK)
            .setSearchHint("Buscar en YouTube o escribir un enlace")
            .setShowKeyboardByDefault(true)
            .build()
}
