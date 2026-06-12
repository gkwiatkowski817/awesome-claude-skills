<?php
/**
 * Glowny szablon (lista wpisow / archiwa).
 *
 * @package Dworek_Komorno
 */

get_header();
?>
<main class="section">
	<div class="container container-narrow">
		<?php if ( have_posts() ) : ?>
			<?php if ( is_archive() || is_search() ) : ?>
				<h1 class="page-title"><?php is_search() ? printf( esc_html__( 'Wyniki wyszukiwania: %s', 'dworek-komorno' ), esc_html( get_search_query() ) ) : the_archive_title(); ?></h1>
			<?php endif; ?>
			<?php while ( have_posts() ) : the_post(); ?>
				<article <?php post_class( 'post-list-item' ); ?>>
					<h2><a href="<?php the_permalink(); ?>"><?php the_title(); ?></a></h2>
					<p class="post-meta"><?php echo esc_html( get_the_date() ); ?></p>
					<?php the_excerpt(); ?>
				</article>
			<?php endwhile; ?>
			<?php the_posts_pagination(); ?>
		<?php else : ?>
			<p><?php esc_html_e( 'Brak treści do wyświetlenia.', 'dworek-komorno' ); ?></p>
		<?php endif; ?>
	</div>
</main>
<?php get_footer(); ?>
