<?php
/**
 * Panel administracyjny CRM:
 * - menu "Dworek CRM" z pulpitem sprzedazy,
 * - kolumny i szybka zmiana statusu na liscie zamowien,
 * - metaboksy zamowienia (szczegoly tortu, klient, status),
 * - metaboks klienta (dane + historia zamowien).
 *
 * @package Dworek_CRM
 */

defined( 'ABSPATH' ) || exit;

/* =====================================================
   Menu i pulpit sprzedazy
   ===================================================== */

add_action( 'admin_menu', function () {
	add_menu_page(
		__( 'Dworek CRM', 'dworek-crm' ),
		__( 'Dworek CRM', 'dworek-crm' ),
		'edit_posts',
		'dwk-crm',
		'dwk_crm_render_dashboard',
		'dashicons-store',
		26
	);
	add_submenu_page( 'dwk-crm', __( 'Pulpit sprzedaży', 'dworek-crm' ), __( 'Pulpit sprzedaży', 'dworek-crm' ), 'edit_posts', 'dwk-crm', 'dwk_crm_render_dashboard' );
} );

function dwk_crm_render_dashboard() {
	$stats    = dwk_crm_sales_stats();
	$statuses = dwk_crm_order_statuses();

	// Najblizsze realizacje (zamowienia z terminem >= dzis, niezakonczone).
	$upcoming = get_posts( array(
		'post_type'      => 'dwk_order',
		'post_status'    => array( 'dwk-new', 'dwk-confirmed', 'dwk-production', 'dwk-ready' ),
		'posts_per_page' => 10,
		'meta_key'       => '_dwk_date',
		'orderby'        => 'meta_value',
		'order'          => 'ASC',
		'meta_query'     => array(
			array(
				'key'     => '_dwk_date',
				'value'   => gmdate( 'Y-m-d' ),
				'compare' => '>=',
				'type'    => 'DATE',
			),
		),
	) );
	?>
	<div class="wrap dwk-crm-dashboard">
		<h1><?php esc_html_e( 'Dworek CRM — pulpit sprzedaży', 'dworek-crm' ); ?></h1>

		<div class="dwk-crm-cards">
			<div class="dwk-crm-card">
				<span class="dwk-crm-card-value"><?php echo esc_html( number_format_i18n( $stats['revenue_month'], 2 ) ); ?> zł</span>
				<span class="dwk-crm-card-label"><?php esc_html_e( 'Przychód w tym miesiącu', 'dworek-crm' ); ?></span>
			</div>
			<div class="dwk-crm-card">
				<span class="dwk-crm-card-value"><?php echo esc_html( $stats['orders_month'] ); ?></span>
				<span class="dwk-crm-card-label"><?php esc_html_e( 'Zamówienia w tym miesiącu', 'dworek-crm' ); ?></span>
			</div>
			<div class="dwk-crm-card">
				<span class="dwk-crm-card-value"><?php echo esc_html( number_format_i18n( $stats['revenue_total'], 2 ) ); ?> zł</span>
				<span class="dwk-crm-card-label"><?php esc_html_e( 'Przychód łącznie', 'dworek-crm' ); ?></span>
			</div>
			<div class="dwk-crm-card">
				<span class="dwk-crm-card-value"><?php echo esc_html( $stats['customers'] ); ?></span>
				<span class="dwk-crm-card-label"><?php esc_html_e( 'Klienci w bazie', 'dworek-crm' ); ?></span>
			</div>
		</div>

		<h2><?php esc_html_e( 'Pipeline zamówień', 'dworek-crm' ); ?></h2>
		<div class="dwk-crm-pipeline">
			<?php foreach ( $statuses as $status => $label ) : ?>
				<a class="dwk-crm-pipeline-step dwk-crm-status-<?php echo esc_attr( $status ); ?>"
					href="<?php echo esc_url( admin_url( 'edit.php?post_type=dwk_order&post_status=' . $status ) ); ?>">
					<span class="dwk-crm-pipeline-count"><?php echo esc_html( $stats['by_status'][ $status ] ); ?></span>
					<span><?php echo esc_html( $label ); ?></span>
				</a>
			<?php endforeach; ?>
		</div>

		<h2><?php esc_html_e( 'Najbliższe realizacje', 'dworek-crm' ); ?></h2>
		<?php if ( $upcoming ) : ?>
			<table class="widefat striped">
				<thead>
					<tr>
						<th><?php esc_html_e( 'Termin', 'dworek-crm' ); ?></th>
						<th><?php esc_html_e( 'Zamówienie', 'dworek-crm' ); ?></th>
						<th><?php esc_html_e( 'Klient', 'dworek-crm' ); ?></th>
						<th><?php esc_html_e( 'Status', 'dworek-crm' ); ?></th>
						<th><?php esc_html_e( 'Kwota', 'dworek-crm' ); ?></th>
					</tr>
				</thead>
				<tbody>
					<?php foreach ( $upcoming as $order ) : ?>
						<tr>
							<td><strong><?php echo esc_html( get_post_meta( $order->ID, '_dwk_date', true ) ); ?></strong></td>
							<td><a href="<?php echo esc_url( get_edit_post_link( $order->ID ) ); ?>">#<?php echo esc_html( $order->ID ); ?> <?php echo esc_html( $order->post_title ); ?></a></td>
							<td><?php echo esc_html( get_post_meta( $order->ID, '_dwk_name', true ) ); ?>, <?php echo esc_html( get_post_meta( $order->ID, '_dwk_phone', true ) ); ?></td>
							<td><?php echo esc_html( $statuses[ $order->post_status ] ?? $order->post_status ); ?></td>
							<td><?php echo esc_html( number_format_i18n( (float) get_post_meta( $order->ID, '_dwk_total', true ), 2 ) ); ?> zł</td>
						</tr>
					<?php endforeach; ?>
				</tbody>
			</table>
		<?php else : ?>
			<p><?php esc_html_e( 'Brak nadchodzących zamówień.', 'dworek-crm' ); ?></p>
		<?php endif; ?>

		<p style="margin-top:20px;">
			<a class="button button-primary" href="<?php echo esc_url( admin_url( 'admin-post.php?action=dwk_crm_export&nonce=' . wp_create_nonce( 'dwk_crm_export' ) ) ); ?>">
				<?php esc_html_e( 'Eksportuj zamówienia do CSV', 'dworek-crm' ); ?>
			</a>
		</p>
	</div>
	<?php
}

/* =====================================================
   Lista zamowien: kolumny
   ===================================================== */

add_filter( 'manage_dwk_order_posts_columns', function ( $columns ) {
	return array(
		'cb'           => $columns['cb'],
		'title'        => __( 'Zamówienie', 'dworek-crm' ),
		'dwk_date'     => __( 'Termin', 'dworek-crm' ),
		'dwk_customer' => __( 'Klient', 'dworek-crm' ),
		'dwk_cake'     => __( 'Tort', 'dworek-crm' ),
		'dwk_total'    => __( 'Kwota', 'dworek-crm' ),
		'dwk_status'   => __( 'Status', 'dworek-crm' ),
	);
} );

add_action( 'manage_dwk_order_posts_custom_column', function ( $column, $post_id ) {
	switch ( $column ) {
		case 'dwk_date':
			echo '<strong>' . esc_html( get_post_meta( $post_id, '_dwk_date', true ) ) . '</strong>';
			break;
		case 'dwk_customer':
			echo esc_html( get_post_meta( $post_id, '_dwk_name', true ) ) . '<br>';
			echo esc_html( get_post_meta( $post_id, '_dwk_phone', true ) );
			break;
		case 'dwk_cake':
			$cfg = function_exists( 'dwk_cb_get_config' ) ? dwk_cb_get_config() : null;
			$shape  = get_post_meta( $post_id, '_dwk_shape', true );
			$size   = get_post_meta( $post_id, '_dwk_size', true );
			$flavor = get_post_meta( $post_id, '_dwk_flavor', true );
			if ( $cfg ) {
				$parts = array(
					$cfg['shapes'][ $shape ]['label'] ?? $shape,
					$cfg['sizes'][ $size ]['label'] ?? $size,
					$cfg['flavors'][ $flavor ]['label'] ?? $flavor,
				);
				echo esc_html( implode( ' · ', array_filter( $parts ) ) );
			} else {
				echo esc_html( implode( ' · ', array_filter( array( $shape, $size, $flavor ) ) ) );
			}
			break;
		case 'dwk_total':
			echo esc_html( number_format_i18n( (float) get_post_meta( $post_id, '_dwk_total', true ), 2 ) ) . ' zł';
			break;
		case 'dwk_status':
			$statuses = dwk_crm_order_statuses();
			$status   = get_post_status( $post_id );
			echo '<span class="dwk-crm-badge dwk-crm-status-' . esc_attr( $status ) . '">' . esc_html( $statuses[ $status ] ?? $status ) . '</span>';
			break;
	}
}, 10, 2 );

add_filter( 'manage_edit-dwk_order_sortable_columns', function ( $columns ) {
	$columns['dwk_date']  = 'dwk_date';
	$columns['dwk_total'] = 'dwk_total';
	return $columns;
} );

add_action( 'pre_get_posts', function ( $query ) {
	if ( ! is_admin() || ! $query->is_main_query() || 'dwk_order' !== $query->get( 'post_type' ) ) {
		return;
	}
	$orderby = $query->get( 'orderby' );
	if ( 'dwk_date' === $orderby ) {
		$query->set( 'meta_key', '_dwk_date' );
		$query->set( 'orderby', 'meta_value' );
	} elseif ( 'dwk_total' === $orderby ) {
		$query->set( 'meta_key', '_dwk_total' );
		$query->set( 'orderby', 'meta_value_num' );
	}
} );

/* =====================================================
   Metabox zamowienia: szczegoly + status
   ===================================================== */

add_action( 'add_meta_boxes', function () {
	add_meta_box( 'dwk-order-details', __( 'Szczegóły zamówienia', 'dworek-crm' ), 'dwk_crm_order_metabox', 'dwk_order', 'normal', 'high' );
	add_meta_box( 'dwk-order-status', __( 'Status zamówienia', 'dworek-crm' ), 'dwk_crm_status_metabox', 'dwk_order', 'side', 'high' );
	add_meta_box( 'dwk-customer-details', __( 'Dane klienta i historia', 'dworek-crm' ), 'dwk_crm_customer_metabox', 'dwk_customer', 'normal', 'high' );
} );

function dwk_crm_order_metabox( $post ) {
	$cfg = function_exists( 'dwk_cb_get_config' ) ? dwk_cb_get_config() : null;
	$meta = function ( $key ) use ( $post ) {
		return get_post_meta( $post->ID, '_dwk_' . $key, true );
	};
	$label = function ( $group, $key ) use ( $cfg ) {
		if ( $cfg && isset( $cfg[ $group ][ $key ]['label'] ) ) {
			return $cfg[ $group ][ $key ]['label'];
		}
		if ( $cfg && isset( $cfg[ $group ][ $key ] ) && is_string( $cfg[ $group ][ $key ] ) ) {
			return $cfg[ $group ][ $key ];
		}
		return $key;
	};

	$extras = (array) $meta( 'extras' );
	$extras_labels = array_map( function ( $extra ) use ( $label ) {
		return $label( 'extras', $extra );
	}, $extras );

	$photo_id = (int) $meta( 'photo_id' );

	$rows = array(
		__( 'Kształt', 'dworek-crm' )      => $label( 'shapes', $meta( 'shape' ) ),
		__( 'Rozmiar', 'dworek-crm' )      => $label( 'sizes', $meta( 'size' ) ),
		__( 'Smak', 'dworek-crm' )         => $label( 'flavors', $meta( 'flavor' ) ),
		__( 'Wykończenie', 'dworek-crm' )  => $label( 'finishes', $meta( 'finish' ) ),
		__( 'Dekoracje', 'dworek-crm' )    => $extras_labels ? implode( ', ', $extras_labels ) : __( 'brak', 'dworek-crm' ),
		__( 'Napis', 'dworek-crm' )        => $meta( 'inscription' ) ?: __( 'brak', 'dworek-crm' ),
		__( 'Okazja', 'dworek-crm' )       => $label( 'occasions', $meta( 'occasion' ) ),
		__( 'Uwagi', 'dworek-crm' )        => $meta( 'notes' ) ?: '—',
		__( 'Termin', 'dworek-crm' )       => $meta( 'date' ),
		__( 'Dostawa', 'dworek-crm' )      => $label( 'delivery', $meta( 'delivery' ) ),
		__( 'Adres', 'dworek-crm' )        => $meta( 'address' ) ?: '—',
		__( 'Klient', 'dworek-crm' )       => $meta( 'name' ) . ', tel. ' . $meta( 'phone' ) . ', ' . $meta( 'email' ),
		__( 'Źródło', 'dworek-crm' )       => $meta( 'source' ),
		__( 'Kwota', 'dworek-crm' )        => number_format_i18n( (float) $meta( 'total' ), 2 ) . ' zł',
	);

	echo '<table class="widefat striped dwk-crm-order-table"><tbody>';
	foreach ( $rows as $row_label => $value ) {
		echo '<tr><th style="width:180px;">' . esc_html( $row_label ) . '</th><td>' . esc_html( $value ) . '</td></tr>';
	}
	if ( $photo_id ) {
		echo '<tr><th>' . esc_html__( 'Własna grafika', 'dworek-crm' ) . '</th><td>' . wp_get_attachment_image( $photo_id, 'medium' );
		$transform = $meta( 'transform' );
		if ( $transform ) {
			echo '<br><code>' . esc_html__( 'Kadr (x, y, skala, obrót): ', 'dworek-crm' ) . esc_html( $transform ) . '</code>';
		}
		echo '<br><a href="' . esc_url( wp_get_attachment_url( $photo_id ) ) . '" target="_blank">' . esc_html__( 'Pobierz oryginał', 'dworek-crm' ) . '</a></td></tr>';
	}
	$customer_id = (int) $meta( 'customer_id' );
	if ( $customer_id ) {
		echo '<tr><th>' . esc_html__( 'Kartoteka klienta', 'dworek-crm' ) . '</th><td><a href="' . esc_url( get_edit_post_link( $customer_id ) ) . '">' . esc_html( get_the_title( $customer_id ) ) . '</a></td></tr>';
	}
	echo '</tbody></table>';
}

function dwk_crm_status_metabox( $post ) {
	wp_nonce_field( 'dwk_crm_save_status', 'dwk_crm_status_nonce' );
	$current = get_post_status( $post->ID );
	echo '<select name="dwk_crm_order_status" style="width:100%;">';
	foreach ( dwk_crm_order_statuses() as $status => $status_label ) {
		printf(
			'<option value="%s" %s>%s</option>',
			esc_attr( $status ),
			selected( $current, $status, false ),
			esc_html( $status_label )
		);
	}
	echo '</select>';
	echo '<p class="description">' . esc_html__( 'Po zapisaniu klient otrzyma e-mail o zmianie statusu.', 'dworek-crm' ) . '</p>';
}

add_action( 'save_post_dwk_order', function ( $post_id ) {
	if ( defined( 'DOING_AUTOSAVE' ) && DOING_AUTOSAVE ) {
		return;
	}
	if ( empty( $_POST['dwk_crm_status_nonce'] ) || ! wp_verify_nonce( sanitize_key( $_POST['dwk_crm_status_nonce'] ), 'dwk_crm_save_status' ) ) {
		return;
	}
	if ( ! current_user_can( 'edit_post', $post_id ) ) {
		return;
	}
	$status = sanitize_key( $_POST['dwk_crm_order_status'] ?? '' );
	if ( ! isset( dwk_crm_order_statuses()[ $status ] ) || get_post_status( $post_id ) === $status ) {
		return;
	}
	// Bez rekurencji save_post.
	remove_all_actions( 'save_post_dwk_order' );
	wp_update_post( array( 'ID' => $post_id, 'post_status' => $status ) );
} );

/* =====================================================
   Metabox klienta: dane + historia zamowien
   ===================================================== */

function dwk_crm_customer_metabox( $post ) {
	$email   = get_post_meta( $post->ID, '_dwk_email', true );
	$phone   = get_post_meta( $post->ID, '_dwk_phone', true );
	$address = get_post_meta( $post->ID, '_dwk_address', true );
	$orders  = dwk_crm_get_customer_orders( $post->ID );
	$statuses = dwk_crm_order_statuses();

	$total = 0;
	foreach ( $orders as $order ) {
		if ( 'dwk-cancelled' !== $order->post_status ) {
			$total += (float) get_post_meta( $order->ID, '_dwk_total', true );
		}
	}

	echo '<p><strong>' . esc_html__( 'E-mail:', 'dworek-crm' ) . '</strong> ' . esc_html( $email ?: '—' ) . '<br>';
	echo '<strong>' . esc_html__( 'Telefon:', 'dworek-crm' ) . '</strong> ' . esc_html( $phone ?: '—' ) . '<br>';
	echo '<strong>' . esc_html__( 'Adres:', 'dworek-crm' ) . '</strong> ' . esc_html( $address ?: '—' ) . '</p>';
	echo '<p><strong>' . esc_html__( 'Liczba zamówień:', 'dworek-crm' ) . '</strong> ' . count( $orders );
	echo ' · <strong>' . esc_html__( 'Wartość łączna:', 'dworek-crm' ) . '</strong> ' . esc_html( number_format_i18n( $total, 2 ) ) . ' zł</p>';

	if ( $orders ) {
		echo '<table class="widefat striped"><thead><tr><th>#</th><th>' . esc_html__( 'Termin', 'dworek-crm' ) . '</th><th>' . esc_html__( 'Status', 'dworek-crm' ) . '</th><th>' . esc_html__( 'Kwota', 'dworek-crm' ) . '</th></tr></thead><tbody>';
		foreach ( $orders as $order ) {
			echo '<tr><td><a href="' . esc_url( get_edit_post_link( $order->ID ) ) . '">#' . esc_html( $order->ID ) . '</a></td>';
			echo '<td>' . esc_html( get_post_meta( $order->ID, '_dwk_date', true ) ) . '</td>';
			echo '<td>' . esc_html( $statuses[ $order->post_status ] ?? $order->post_status ) . '</td>';
			echo '<td>' . esc_html( number_format_i18n( (float) get_post_meta( $order->ID, '_dwk_total', true ), 2 ) ) . ' zł</td></tr>';
		}
		echo '</tbody></table>';
	} else {
		echo '<p>' . esc_html__( 'Brak zamówień.', 'dworek-crm' ) . '</p>';
	}
}
