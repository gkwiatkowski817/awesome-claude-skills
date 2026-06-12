<?php
/**
 * Renderowanie formularza kreatora tortow (HTML).
 *
 * @package Dworek_Cake_Builder
 */

defined( 'ABSPATH' ) || exit;

function dwk_cb_render_form() {
	$cfg = dwk_cb_get_config();

	ob_start();
	?>
	<form id="dwk-cake-builder" class="dwk-cb" enctype="multipart/form-data" novalidate>

		<!-- KROK 1: Ksztalt -->
		<fieldset class="dwk-cb-step" data-step="1">
			<legend><span class="dwk-cb-step-num">1</span> Wybierz kształt tortu <span class="dwk-cb-req">*</span></legend>
			<div class="dwk-cb-options dwk-cb-shapes">
				<?php foreach ( $cfg['shapes'] as $key => $shape ) : ?>
					<label class="dwk-cb-option">
						<input type="radio" name="shape" value="<?php echo esc_attr( $key ); ?>" required>
						<span class="dwk-cb-option-box">
							<span class="dwk-cb-shape-icon dwk-cb-shape-<?php echo esc_attr( $key ); ?>" aria-hidden="true"></span>
							<?php echo esc_html( $shape['label'] ); ?>
						</span>
					</label>
				<?php endforeach; ?>
			</div>
		</fieldset>

		<!-- KROK 2: Rozmiar (filtrowany wg ksztaltu; pokazuje wage i porcje) -->
		<fieldset class="dwk-cb-step" data-step="2">
			<legend><span class="dwk-cb-step-num">2</span> Wybierz rozmiar <span class="dwk-cb-req">*</span></legend>
			<p class="dwk-cb-hint">Najpierw wybierz kształt — lista rozmiarów zostanie do niego dopasowana.</p>
			<div class="dwk-cb-options dwk-cb-sizes">
				<?php foreach ( $cfg['sizes'] as $key => $size ) : ?>
					<label class="dwk-cb-option" data-shape="<?php echo esc_attr( $size['shape'] ); ?>" hidden>
						<input type="radio" name="size" value="<?php echo esc_attr( $key ); ?>" required>
						<span class="dwk-cb-option-box">
							<strong><?php echo esc_html( $size['label'] ); ?></strong>
							<small>waga ok. <?php echo esc_html( $size['weight'] ); ?> &middot; ok. <?php echo esc_html( $size['portions'] ); ?> porcji</small>
							<em><?php echo esc_html( number_format_i18n( $size['price'], 0 ) ); ?> zł</em>
						</span>
					</label>
				<?php endforeach; ?>
			</div>
		</fieldset>

		<!-- KROK 3: Smak -->
		<fieldset class="dwk-cb-step" data-step="3">
			<legend><span class="dwk-cb-step-num">3</span> Wybierz smak <span class="dwk-cb-req">*</span></legend>
			<div class="dwk-cb-options dwk-cb-flavors">
				<?php foreach ( $cfg['flavors'] as $key => $flavor ) : ?>
					<label class="dwk-cb-option dwk-cb-option-wide">
						<input type="radio" name="flavor" value="<?php echo esc_attr( $key ); ?>" required>
						<span class="dwk-cb-option-box">
							<strong><?php echo esc_html( $flavor['label'] ); ?><?php if ( $flavor['surcharge'] > 0 ) : ?> <em>+<?php echo esc_html( $flavor['surcharge'] ); ?> zł</em><?php endif; ?></strong>
							<small><?php echo esc_html( $flavor['desc'] ); ?></small>
						</span>
					</label>
				<?php endforeach; ?>
			</div>
		</fieldset>

		<!-- KROK 4: Wykonczenie -->
		<fieldset class="dwk-cb-step" data-step="4">
			<legend><span class="dwk-cb-step-num">4</span> Wykończenie tortu <span class="dwk-cb-req">*</span></legend>
			<div class="dwk-cb-options">
				<?php foreach ( $cfg['finishes'] as $key => $finish ) : ?>
					<label class="dwk-cb-option dwk-cb-option-wide">
						<input type="radio" name="finish" value="<?php echo esc_attr( $key ); ?>" <?php checked( 'krem', $key ); ?> required>
						<span class="dwk-cb-option-box">
							<strong><?php echo esc_html( $finish['label'] ); ?><?php if ( $finish['surcharge'] > 0 ) : ?> <em>+<?php echo esc_html( $finish['surcharge'] ); ?> zł</em><?php endif; ?></strong>
						</span>
					</label>
				<?php endforeach; ?>
			</div>
		</fieldset>

		<!-- KROK 5: Dekoracje (wielokrotny wybor) -->
		<fieldset class="dwk-cb-step" data-step="5">
			<legend><span class="dwk-cb-step-num">5</span> Dekoracje <span class="dwk-cb-optional">(opcjonalnie)</span></legend>
			<div class="dwk-cb-options">
				<?php foreach ( $cfg['extras'] as $key => $extra ) : ?>
					<label class="dwk-cb-option dwk-cb-option-wide">
						<input type="checkbox" name="extras[]" value="<?php echo esc_attr( $key ); ?>">
						<span class="dwk-cb-option-box">
							<strong><?php echo esc_html( $extra['label'] ); ?> <em>+<?php echo esc_html( $extra['price'] ); ?> zł</em></strong>
						</span>
					</label>
				<?php endforeach; ?>
			</div>
		</fieldset>

		<!-- KROK 6: Wlasna grafika + edytor -->
		<fieldset class="dwk-cb-step" data-step="6">
			<legend><span class="dwk-cb-step-num">6</span> Własna grafika <span class="dwk-cb-optional">(opcjonalnie, +<?php echo esc_html( $cfg['photo_price'] ); ?> zł)</span></legend>
			<p class="dwk-cb-hint">Wgraj zdjęcie lub grafikę (JPG, PNG, WEBP, maks. <?php echo esc_html( $cfg['photo_max_mb'] ); ?> MB) — wydrukujemy je na jadalnym opłatku. Po wgraniu możesz przesunąć, przeskalować i obrócić obraz w podglądzie.</p>
			<input type="file" id="dwk-cb-photo" name="photo" accept="image/jpeg,image/png,image/webp">
			<div class="dwk-cb-photo-editor" hidden>
				<div class="dwk-cb-photo-stage" id="dwk-cb-photo-stage">
					<img id="dwk-cb-photo-preview" src="" alt="Podgląd grafiki na torcie" draggable="false">
				</div>
				<div class="dwk-cb-photo-controls">
					<label>Powiększenie
						<input type="range" id="dwk-cb-photo-scale" min="0.3" max="3" step="0.05" value="1">
					</label>
					<label>Obrót
						<input type="range" id="dwk-cb-photo-rotate" min="-180" max="180" step="1" value="0">
					</label>
					<button type="button" class="dwk-cb-btn-link" id="dwk-cb-photo-remove">Usuń grafikę</button>
					<p class="dwk-cb-hint">Przeciągnij obraz, aby ustawić kadr.</p>
				</div>
			</div>
			<input type="hidden" name="photo_transform" id="dwk-cb-photo-transform" value="">
		</fieldset>

		<!-- KROK 7: Napis i okazja -->
		<fieldset class="dwk-cb-step" data-step="7">
			<legend><span class="dwk-cb-step-num">7</span> Napis na torcie i okazja</legend>
			<p class="dwk-cb-field">
				<label for="dwk-cb-inscription">Napis na torcie <span class="dwk-cb-optional">(np. „Sto lat, Aniu!”, maks. <?php echo esc_html( $cfg['inscription_max'] ); ?> znaków)</span></label>
				<input type="text" id="dwk-cb-inscription" name="inscription" maxlength="<?php echo esc_attr( $cfg['inscription_max'] ); ?>">
			</p>
			<p class="dwk-cb-field">
				<label for="dwk-cb-occasion">Okazja</label>
				<select id="dwk-cb-occasion" name="occasion">
					<?php foreach ( $cfg['occasions'] as $key => $label ) : ?>
						<option value="<?php echo esc_attr( $key ); ?>"><?php echo esc_html( $label ); ?></option>
					<?php endforeach; ?>
				</select>
			</p>
			<p class="dwk-cb-field">
				<label for="dwk-cb-notes">Dodatkowe uwagi dla cukiernika</label>
				<textarea id="dwk-cb-notes" name="notes" rows="3" maxlength="600"></textarea>
			</p>
		</fieldset>

		<!-- KROK 8: Termin i dostawa -->
		<fieldset class="dwk-cb-step" data-step="8">
			<legend><span class="dwk-cb-step-num">8</span> Termin i sposób odbioru <span class="dwk-cb-req">*</span></legend>
			<div class="dwk-cb-options">
				<?php foreach ( $cfg['delivery'] as $key => $delivery ) : ?>
					<label class="dwk-cb-option dwk-cb-option-wide">
						<input type="radio" name="delivery" value="<?php echo esc_attr( $key ); ?>" data-address="<?php echo $delivery['address'] ? '1' : '0'; ?>" <?php checked( 'odbior', $key ); ?> required>
						<span class="dwk-cb-option-box">
							<strong><?php echo esc_html( $delivery['label'] ); ?> <em><?php echo $delivery['price'] > 0 ? '+' . esc_html( $delivery['price'] ) . ' zł' : 'gratis'; ?></em></strong>
						</span>
					</label>
				<?php endforeach; ?>
			</div>
			<p class="dwk-cb-field">
				<label for="dwk-cb-date">Data odbioru / dostawy <span class="dwk-cb-req">*</span></label>
				<input type="date" id="dwk-cb-date" name="date" required>
				<span class="dwk-cb-hint" id="dwk-cb-date-hint"></span>
			</p>
			<div class="dwk-cb-address" hidden>
				<p class="dwk-cb-field">
					<label for="dwk-cb-address">Adres dostawy <span class="dwk-cb-req">*</span></label>
					<input type="text" id="dwk-cb-address" name="address" placeholder="ulica i numer, kod pocztowy, miejscowość">
				</p>
			</div>
		</fieldset>

		<!-- KROK 9: Dane zamawiajacego -->
		<fieldset class="dwk-cb-step" data-step="9">
			<legend><span class="dwk-cb-step-num">9</span> Twoje dane <span class="dwk-cb-req">*</span></legend>
			<div class="dwk-cb-fields-grid">
				<p class="dwk-cb-field">
					<label for="dwk-cb-name">Imię i nazwisko <span class="dwk-cb-req">*</span></label>
					<input type="text" id="dwk-cb-name" name="customer_name" required>
				</p>
				<p class="dwk-cb-field">
					<label for="dwk-cb-phone">Telefon <span class="dwk-cb-req">*</span></label>
					<input type="tel" id="dwk-cb-phone" name="customer_phone" required>
				</p>
				<p class="dwk-cb-field">
					<label for="dwk-cb-email">E-mail <span class="dwk-cb-req">*</span></label>
					<input type="email" id="dwk-cb-email" name="customer_email" required>
				</p>
			</div>
			<p class="dwk-cb-field dwk-cb-consent">
				<label>
					<input type="checkbox" name="consent" value="1" required>
					Akceptuję <a href="/regulamin/" target="_blank" rel="noopener">regulamin</a> i <a href="/polityka-prywatnosci/" target="_blank" rel="noopener">politykę prywatności</a>. <span class="dwk-cb-req">*</span>
				</label>
			</p>
		</fieldset>

		<!-- Podsumowanie ceny + wysylka -->
		<div class="dwk-cb-summary">
			<div class="dwk-cb-summary-lines" id="dwk-cb-summary-lines"></div>
			<p class="dwk-cb-total">Razem: <strong id="dwk-cb-total">0 zł</strong></p>
			<p class="dwk-cb-hint">Cena ma charakter orientacyjny — ostateczną kwotę potwierdzimy telefonicznie wraz z zamówieniem.</p>
			<button type="submit" class="dwk-cb-submit">Zamów tort</button>
			<div class="dwk-cb-message" id="dwk-cb-message" role="status" aria-live="polite"></div>
		</div>
	</form>
	<?php
	return ob_get_clean();
}
