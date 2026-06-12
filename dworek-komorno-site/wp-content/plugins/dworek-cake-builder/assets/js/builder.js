/**
 * Dworek Cake Builder - logika kreatora tortow:
 * - filtrowanie rozmiarow wg ksztaltu,
 * - kalkulacja ceny na zywo,
 * - edytor wgranej grafiki (przesuwanie, skalowanie, obracanie),
 * - walidacja terminu, pole adresu zalezne od dostawy,
 * - wysylka AJAX (FormData z plikiem).
 */
( function () {
	'use strict';

	document.addEventListener( 'DOMContentLoaded', function () {
		var form = document.getElementById( 'dwk-cake-builder' );
		if ( ! form || typeof dwkCakeBuilder === 'undefined' ) {
			return;
		}

		var cfg = dwkCakeBuilder.config;
		var i18n = dwkCakeBuilder.i18n;

		/* ---------- Rozmiary zalezne od ksztaltu ---------- */
		function refreshSizes() {
			var shapeInput = form.querySelector( 'input[name="shape"]:checked' );
			var shape = shapeInput ? shapeInput.value : null;
			form.querySelectorAll( '.dwk-cb-sizes .dwk-cb-option' ).forEach( function ( option ) {
				var visible = shape && option.dataset.shape === shape;
				option.hidden = ! visible;
				var radio = option.querySelector( 'input' );
				if ( ! visible && radio.checked ) {
					radio.checked = false;
				}
			} );
		}

		/* ---------- Termin: minimalna data ---------- */
		var dateInput = document.getElementById( 'dwk-cb-date' );
		var dateHint = document.getElementById( 'dwk-cb-date-hint' );

		function leadDays() {
			var delivery = form.querySelector( 'input[name="delivery"]:checked' );
			return delivery && delivery.value === 'express' ? cfg.min_lead_days_express : cfg.min_lead_days;
		}

		function refreshMinDate() {
			var days = leadDays();
			var min = new Date();
			min.setDate( min.getDate() + days );
			var iso = min.toISOString().slice( 0, 10 );
			dateInput.min = iso;
			if ( dateInput.value && dateInput.value < iso ) {
				dateInput.value = iso;
			}
			dateHint.textContent = 'Najwcześniejszy termin: ' + iso + ' (' + days + ' dni od dziś).';
		}

		/* ---------- Adres zalezny od sposobu dostawy ---------- */
		function refreshAddress() {
			var delivery = form.querySelector( 'input[name="delivery"]:checked' );
			var needsAddress = delivery && delivery.dataset.address === '1';
			var wrap = form.querySelector( '.dwk-cb-address' );
			wrap.hidden = ! needsAddress;
			document.getElementById( 'dwk-cb-address' ).required = !! needsAddress;
		}

		/* ---------- Kalkulacja ceny ---------- */
		var summaryLines = document.getElementById( 'dwk-cb-summary-lines' );
		var totalEl = document.getElementById( 'dwk-cb-total' );

		function val( name ) {
			var input = form.querySelector( 'input[name="' + name + '"]:checked' );
			return input ? input.value : null;
		}

		function refreshPrice() {
			var lines = [];
			var total = 0;

			var size = val( 'size' );
			if ( size && cfg.sizes[ size ] ) {
				var s = cfg.sizes[ size ];
				lines.push( [ 'Tort ' + cfg.shapes[ val( 'shape' ) ].label.toLowerCase() + ' ' + s.label + ' (' + i18n.weight + ' ' + s.weight + ', ~' + s.portions + ' ' + i18n.portions + ')', s.price ] );
				total += s.price;
			}
			var flavor = val( 'flavor' );
			if ( flavor && cfg.flavors[ flavor ] ) {
				lines.push( [ 'Smak: ' + cfg.flavors[ flavor ].label, cfg.flavors[ flavor ].surcharge ] );
				total += cfg.flavors[ flavor ].surcharge;
			}
			var finish = val( 'finish' );
			if ( finish && cfg.finishes[ finish ] ) {
				lines.push( [ 'Wykończenie: ' + cfg.finishes[ finish ].label, cfg.finishes[ finish ].surcharge ] );
				total += cfg.finishes[ finish ].surcharge;
			}
			form.querySelectorAll( 'input[name="extras[]"]:checked' ).forEach( function ( box ) {
				var extra = cfg.extras[ box.value ];
				if ( extra ) {
					lines.push( [ extra.label, extra.price ] );
					total += extra.price;
				}
			} );
			if ( photoFile ) {
				lines.push( [ 'Własna grafika (wydruk na opłatku)', cfg.photo_price ] );
				total += cfg.photo_price;
			}
			var delivery = val( 'delivery' );
			if ( delivery && cfg.delivery[ delivery ] ) {
				lines.push( [ cfg.delivery[ delivery ].label, cfg.delivery[ delivery ].price ] );
				total += cfg.delivery[ delivery ].price;
			}

			summaryLines.innerHTML = '';
			lines.forEach( function ( line ) {
				var row = document.createElement( 'p' );
				row.className = 'dwk-cb-summary-line';
				row.textContent = line[ 0 ] + ' — ' + ( line[ 1 ] > 0 ? line[ 1 ] + ' zł' : ( line[ 1 ] === 0 ? 'w cenie' : '' ) );
				summaryLines.appendChild( row );
			} );
			totalEl.textContent = total + ' zł';
		}

		/* ---------- Edytor grafiki ---------- */
		var photoInput = document.getElementById( 'dwk-cb-photo' );
		var editor = form.querySelector( '.dwk-cb-photo-editor' );
		var stage = document.getElementById( 'dwk-cb-photo-stage' );
		var preview = document.getElementById( 'dwk-cb-photo-preview' );
		var scaleInput = document.getElementById( 'dwk-cb-photo-scale' );
		var rotateInput = document.getElementById( 'dwk-cb-photo-rotate' );
		var transformField = document.getElementById( 'dwk-cb-photo-transform' );
		var removeBtn = document.getElementById( 'dwk-cb-photo-remove' );

		var photoFile = null;
		var pos = { x: 0, y: 0 };
		var dragging = null;

		function applyTransform() {
			var scale = parseFloat( scaleInput.value );
			var rotate = parseInt( rotateInput.value, 10 );
			preview.style.transform = 'translate(-50%, -50%) translate(' + pos.x + 'px,' + pos.y + 'px) scale(' + scale + ') rotate(' + rotate + 'deg)';
			transformField.value = JSON.stringify( { x: pos.x, y: pos.y, scale: scale, rotate: rotate } );
		}

		photoInput.addEventListener( 'change', function () {
			var file = photoInput.files[ 0 ];
			if ( ! file ) {
				return;
			}
			if ( file.size > cfg.photo_max_mb * 1024 * 1024 ) {
				showMessage( i18n.fileTooBig, true );
				photoInput.value = '';
				return;
			}
			if ( [ 'image/jpeg', 'image/png', 'image/webp' ].indexOf( file.type ) === -1 ) {
				showMessage( i18n.fileType, true );
				photoInput.value = '';
				return;
			}
			photoFile = file;
			preview.src = URL.createObjectURL( file );
			editor.hidden = false;
			pos = { x: 0, y: 0 };
			scaleInput.value = '1';
			rotateInput.value = '0';
			applyTransform();
			refreshPrice();
		} );

		removeBtn.addEventListener( 'click', function () {
			photoFile = null;
			photoInput.value = '';
			preview.src = '';
			editor.hidden = true;
			transformField.value = '';
			refreshPrice();
		} );

		scaleInput.addEventListener( 'input', applyTransform );
		rotateInput.addEventListener( 'input', applyTransform );

		function pointerPos( event ) {
			var point = event.touches ? event.touches[ 0 ] : event;
			return { x: point.clientX, y: point.clientY };
		}
		function startDrag( event ) {
			var point = pointerPos( event );
			dragging = { startX: point.x - pos.x, startY: point.y - pos.y };
			event.preventDefault();
		}
		function moveDrag( event ) {
			if ( ! dragging ) {
				return;
			}
			var point = pointerPos( event );
			pos.x = point.x - dragging.startX;
			pos.y = point.y - dragging.startY;
			applyTransform();
			event.preventDefault();
		}
		function endDrag() {
			dragging = null;
		}
		stage.addEventListener( 'mousedown', startDrag );
		stage.addEventListener( 'touchstart', startDrag, { passive: false } );
		window.addEventListener( 'mousemove', moveDrag );
		window.addEventListener( 'touchmove', moveDrag, { passive: false } );
		window.addEventListener( 'mouseup', endDrag );
		window.addEventListener( 'touchend', endDrag );

		/* ---------- Komunikaty ---------- */
		var messageEl = document.getElementById( 'dwk-cb-message' );
		function showMessage( text, isError ) {
			messageEl.textContent = text;
			messageEl.className = 'dwk-cb-message ' + ( isError ? 'is-error' : 'is-success' );
		}

		/* ---------- Wysylka ---------- */
		form.addEventListener( 'submit', function ( event ) {
			event.preventDefault();
			if ( ! form.checkValidity() ) {
				form.reportValidity();
				showMessage( i18n.fillFields, true );
				return;
			}

			var data = new FormData( form );
			data.append( 'action', 'dwk_cb_submit' );
			data.append( 'nonce', dwkCakeBuilder.nonce );
			if ( ! photoFile ) {
				data.delete( 'photo' );
			}

			var submitBtn = form.querySelector( '.dwk-cb-submit' );
			submitBtn.disabled = true;
			showMessage( i18n.sending, false );

			fetch( dwkCakeBuilder.ajaxUrl, { method: 'POST', body: data } )
				.then( function ( response ) { return response.json(); } )
				.then( function ( json ) {
					if ( json.success ) {
						showMessage( json.data.message, false );
						form.reset();
						removeBtn.click();
						refreshSizes();
						refreshPrice();
					} else {
						showMessage( ( json.data && json.data.message ) || i18n.error, true );
						submitBtn.disabled = false;
					}
				} )
				.catch( function () {
					showMessage( i18n.error, true );
					submitBtn.disabled = false;
				} );
		} );

		/* ---------- Nasluchiwanie zmian ---------- */
		form.addEventListener( 'change', function ( event ) {
			if ( event.target.name === 'shape' ) {
				refreshSizes();
			}
			if ( event.target.name === 'delivery' ) {
				refreshAddress();
				refreshMinDate();
			}
			refreshPrice();
		} );

		refreshSizes();
		refreshAddress();
		refreshMinDate();
		refreshPrice();
	} );
} )();
