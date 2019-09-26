package uk.ac.bris.cs.scotlandyard.model;

import static java.util.Arrays.asList;
import static java.util.Collections.emptySet;
import static java.util.Collections.singletonList;
import static java.util.Collections.unmodifiableCollection;
import static java.util.Collections.unmodifiableList;
import static java.util.Collections.unmodifiableSet;
import static java.util.Objects.requireNonNull;
import static uk.ac.bris.cs.scotlandyard.model.Colour.Black;
import static uk.ac.bris.cs.scotlandyard.model.Ticket.Double;
import static uk.ac.bris.cs.scotlandyard.model.Ticket.Secret;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import uk.ac.bris.cs.gamekit.graph.Edge;
import uk.ac.bris.cs.gamekit.graph.Node;
import uk.ac.bris.cs.gamekit.graph.Graph;
import uk.ac.bris.cs.gamekit.graph.ImmutableGraph;


public class ScotlandYardModel implements ScotlandYardGame, Consumer<Move> {

	final private List<Boolean> rounds;
	final private Graph<Integer, Transport> graph;
	private PlayerConfiguration mrX;
	private PlayerConfiguration firstDetective;
	private PlayerConfiguration restOfTheDetectives;
	private ArrayList<ScotlandYardPlayer> players;
	private Set<Colour> winningPlayers = new HashSet<>();
	private int currentPlayerIndex;
	private int currentRound;
	private int mrXLastKnownLocation;
	private Collection<Spectator> spectators = new HashSet<>();
	final private ScotlandYardView view = this;

	//==========================================================================================================
	//CONSTRUCTOR
	//==========================================================================================================
	public ScotlandYardModel(List<Boolean> rounds, Graph<Integer, Transport> graph,
			PlayerConfiguration mrX, PlayerConfiguration firstDetective,
			PlayerConfiguration... restOfTheDetectives) {

		//Check rounds and graph are not null or empty.
		this.rounds = requireNonNull(rounds);
		this.graph = requireNonNull(graph);
		if (rounds.isEmpty()) throw new IllegalArgumentException("Empty rounds");
		if (graph.isEmpty()) throw new IllegalArgumentException("Empty map");

		//Check MrX's colour is Black.
		if (mrX.colour.isDetective()) {
    	throw new IllegalArgumentException("MrX should be Black");
		}

		//Create a list of all PlayerConfigurations.
		ArrayList<PlayerConfiguration> configurations = new ArrayList<>();
		for (PlayerConfiguration configuration : restOfTheDetectives)
				configurations.add(requireNonNull(configuration));
		configurations.add(0, firstDetective);
		configurations.add(0, mrX);

		//Check players do not start on same location.
		Set<Integer> locationCheck = new HashSet<>();
		for (PlayerConfiguration configuration : configurations) {
			if (locationCheck.contains(configuration.location))
				throw new IllegalArgumentException("Duplicate location");
			locationCheck.add(configuration.location);
		}

		//Check all ticket types exist for each player.
		for (PlayerConfiguration configuration : configurations) {
			if (configuration.tickets.containsKey(Ticket.Secret) == false)
				throw new IllegalArgumentException("No secret tickets");
			if (configuration.tickets.containsKey(Ticket.Bus) == false)
				throw new IllegalArgumentException("No bus tickets");
			if (configuration.tickets.containsKey(Ticket.Taxi) == false)
				throw new IllegalArgumentException("No taxi tickets");
			if (configuration.tickets.containsKey(Ticket.Underground) == false)
				throw new IllegalArgumentException("No underground tickets");
			if (configuration.tickets.containsKey(Ticket.Double) == false)
				throw new IllegalArgumentException("No double tickets");
			if (configuration.tickets.containsKey(Ticket.Secret) == false)
				throw new IllegalArgumentException("No secret tickets");
		}

		//Check detectives do not have Double or Secret tickets.
		for (int i = 1; i < configurations.size(); i++) {
			if (configurations.get(i).tickets.get(Ticket.Double) != 0)
				throw new IllegalArgumentException("Detective has double tickets");
			if (configurations.get(i).tickets.get(Ticket.Secret) != 0)
				throw new IllegalArgumentException("Detective has secret tickets");
		}

		//Create a list of ScotlandYardPlayers.
		this.players = new ArrayList<>();
		for (PlayerConfiguration configuration : configurations) {
			ScotlandYardPlayer player = new ScotlandYardPlayer(configuration.player,
					configuration.colour, configuration.location, configuration.tickets);
			players.add(player);
		}
	}

	//==========================================================================================================
	//METHODS
	//==========================================================================================================
	@Override
	public void registerSpectator(Spectator spectator) {
		requireNonNull(spectator);
		if (spectators.contains(spectator)) throw new IllegalArgumentException();
		spectators.add(spectator);
	}

	@Override
	public void unregisterSpectator(Spectator spectator) {
		requireNonNull(spectator);
		if (!spectators.contains(spectator)) throw new IllegalArgumentException();
		spectators.remove(spectator);
	}

	//Returns a list of valid moves for the current player.
	Set<Move> validMoves(ScotlandYardPlayer player) {
  Set<Move> moves = new HashSet<>();
  Collection<Edge<Integer, Transport>> edges;
	edges = getGraph().getEdgesFrom(getGraph().getNode(player.location()));
  for (Edge<Integer, Transport> edge : edges) {
		//Any player, single TicketMoves.
    if (!playerOnNode(edge.destination().value()) && player.hasTickets(Ticket.fromTransport(edge.data())))
				moves.add(new TicketMove(getCurrentPlayer(), Ticket.fromTransport(edge.data()),
				edge.destination().value()));
		//MrX only, single Secret TicketMoves.
    if (!playerOnNode(edge.destination().value()) && player.colour() == Black && player.hasTickets(Secret))
				moves.add(new TicketMove(Black, Secret, edge.destination().value()));
  	if (player.colour() == Black && !playerOnNode(edge.destination().value())) {
  		Collection<Edge<Integer, Transport>> doubleEdges = getGraph().getEdgesFrom(edge.destination());
  		for (Edge<Integer, Transport> doubleEdge : doubleEdges) {
				//MrX only, check to see if DoubleMove is possible.
      	if (!playerOnNode(doubleEdge.destination().value()) && player.hasTickets(Double) && currentRound <
						rounds.size()-1) {
					//MrX only, DoubleMove, first move normal, second move Secret.
          if (player.hasTickets(Secret) && player.hasTickets(Ticket.fromTransport(edge.data())))
							moves.add(new DoubleMove(Black, new TicketMove(Black, Ticket.fromTransport(edge.data()),
							edge.destination().value()), new TicketMove(Black, Secret, doubleEdge.destination().value())));
					//MrX only, DoubleMove, first move Secret, second move normal.
          if (player.hasTickets(Secret) && player.hasTickets(Ticket.fromTransport(doubleEdge.data())))
							moves.add(new DoubleMove(Black, new TicketMove(Black, Secret, edge.destination().value()), new
							TicketMove(Black, Ticket.fromTransport(doubleEdge.data()), doubleEdge.destination().value())));
					//MrX only, DoubleMove, both moves Secret.
          if (player.hasTickets(Secret, 2)) moves.add(new DoubleMove(Black, new TicketMove(Black, Secret,
							edge.destination().value()), new TicketMove(Black, Secret, doubleEdge.destination().value())));
					//MrX only, DoubleMove, both moves use same transport.
          if (edge.data() == doubleEdge.data() && player.hasTickets(Ticket.fromTransport(edge.data()), 2))
							moves.add(new DoubleMove(Black, new TicketMove(Black, Ticket.fromTransport(edge.data()),
							edge.destination().value()), new TicketMove(Black, Ticket.fromTransport(doubleEdge.data()),
							doubleEdge.destination().value())));
					//MrX only, DoubleMove, two moves use different transport.
					else if (!(edge.data() == doubleEdge.data()) && player.hasTickets(Ticket.fromTransport(edge.data()))
							&& player.hasTickets(Ticket.fromTransport(doubleEdge.data()))) moves.add(new DoubleMove(Black,
							new TicketMove(Black, Ticket.fromTransport(edge.data()), edge.destination().value()), new
							TicketMove(Black, Ticket.fromTransport(doubleEdge.data()), doubleEdge.destination().value())));
          }
				}
			}
		}
		//If list of moves is empty, use a PassMove.
  	if (moves.isEmpty()) moves.add(new PassMove(player.colour()));
    return Collections.unmodifiableSet(moves);
  }

	@Override
	public void startRotate() {
		if (isGameOver()) throw new IllegalStateException();
		players.get(0).player().makeMove(this, players.get(0).location(), validMoves(players.get(0)), this);

	}

	//Check if there is already a player on a node.
	boolean playerOnNode(int value) {
		boolean bool = false;
		for (int i = 1; i < players.size(); i++) {
			if (players.get(i).location() == value) bool = true;
		}
		return bool;
	}

	//Notify all spectators that round is complete.
	void rotationComplete(){
		for (Spectator spectator : spectators) {
			spectator.onRotationComplete(view);
		}
	}

	//Notify all spectators that a move has been made.
	void moveMade(Move move){
		for (Spectator spectator : spectators) {
			spectator.onMoveMade(view, move);
		}
	}

	//Notify all spectators that a new round has begun.
	void roundStart() {
		for (Spectator spectator : spectators) {
			spectator.onRoundStarted(view, currentRound);
		}
	}

	//Notify all spectators that the game is over and who has won.
	void gameOver(){
		for (Spectator spectator : spectators ) {
			spectator.onGameOver(view, getWinningPlayers());
		}
	}


	@Override
	public void accept(Move move) {
		requireNonNull(move);
		if (!validMoves(players.get(currentPlayerIndex)).contains(move)) throw new IllegalArgumentException("Invalid move");
		ScotlandYardPlayer myPlayer = getPlayer(move.colour());
		MoveVisitor visitor = new Visitor();
		move.visit(visitor);
		if (currentRound == rounds.size() && currentPlayerIndex == players.size()) {
			winningPlayers.add(Black);
			gameOver();
		}
		else {
			currentPlayerIndex++;
			if (currentPlayerIndex == players.size()) {
				currentPlayerIndex = 0;
				rotationComplete();
			}
			else if (isGameOver()) gameOver();
			else {
				myPlayer = players.get(currentPlayerIndex);
				myPlayer.player().makeMove(this, myPlayer.location(), validMoves(players.get(currentPlayerIndex)), this);
				if (isGameOver()) gameOver();
			}
		}
	}

	//Returns the ScotlandYardPlayer object for a given colour.
	ScotlandYardPlayer getPlayer(Colour colour) {
		ScotlandYardPlayer playerObject = players.get(0);
		for (ScotlandYardPlayer player : players) {
			if (player.colour() == colour) playerObject = player;
		}
		return playerObject;
	}

	@Override
	public Collection<Spectator> getSpectators() {
		return Collections.unmodifiableCollection(requireNonNull(spectators));
	}

	@Override
	public List<Colour> getPlayers() {
		List<Colour> playerColours = new ArrayList<>();
		for (ScotlandYardPlayer player : players) {
			playerColours.add(player.colour());
		}
		return Collections.unmodifiableList(playerColours);
	}

	@Override
	public Set<Colour> getWinningPlayers() {
		return Collections.unmodifiableSet(winningPlayers);
	}

	@Override
	public int getPlayerLocation(Colour colour) {
		int playerLocation = mrXLastKnownLocation;
		ScotlandYardPlayer player = getPlayer(colour);
		if (player.colour() != Black) playerLocation = player.location();
		return playerLocation;
	}

	@Override
	public int getPlayerTickets(Colour colour, Ticket ticket) {
		int ticketNumber;
		ScotlandYardPlayer player = getPlayer(colour);
		return player.tickets().get(ticket);
	}

	@Override
	public boolean isGameOver() {
		boolean gameOver = false;
		int stuckDetectives = 0;
		if (currentRound == rounds.size() && currentPlayerIndex == 0) {
			winningPlayers.add(Black);
			gameOver = true;
		}
		if (getCurrentPlayer() == Black && validMoves(players.get(0)).iterator().next() instanceof PassMove) {
			for (int i = 1; i < players.size(); i++) {
				winningPlayers.add(players.get(i).colour());
			}
			gameOver = true;
		}
		for (int i = 1; i < players.size(); i++) {
			if (players.get(i).location() == players.get(0).location()) {
				for (int j = 1; j < players.size(); j++) {
					winningPlayers.add(players.get(j).colour());
				}
				gameOver = true;
			}
			if (validMoves(players.get(i)).iterator().next() instanceof PassMove) stuckDetectives++;
		}
		if (stuckDetectives == players.size()-1) {
			winningPlayers.add(Black);
			gameOver = true;
		}
		return gameOver;
	}

	@Override
	public Colour getCurrentPlayer() {
		return players.get(currentPlayerIndex).colour();
	}

	@Override
	public int getCurrentRound() {
		return this.currentRound;
	}

	@Override
	public boolean isRevealRound() {
		return getRounds().get(getCurrentRound());
	}

	public boolean isRevealRoundExt(int round) {
		return getRounds().get(round);
	}


	@Override
	public List<Boolean> getRounds() {
		return Collections.unmodifiableList(rounds);
	}

	@Override
	public Graph<Integer, Transport> getGraph() {
		ImmutableGraph<Integer, Transport> immutableGraph = new ImmutableGraph<>(graph);
		return immutableGraph;
	}

	//==========================================================================================================
	// VISITOR NESTED CLASS
	//==========================================================================================================
	class Visitor implements MoveVisitor {

  	@Override
    public void visit(PassMove move) {
    	moveMade(move);
		}

  	@Override
  	public void visit(TicketMove move) {
  		ScotlandYardPlayer player = getPlayer(move.colour());
			if (player.colour() == Black) {
				player.location(move.destination());
				if (isRevealRound()) mrXLastKnownLocation = move.destination();
				currentRound++;
				roundStart();
  		}
			else players.get(0).addTicket(move.ticket());
			player.removeTicket(move.ticket());
			player.location(move.destination());
      moveMade(move);
		}

  	@Override
  	public void visit(DoubleMove move) {
      players.get(0).removeTicket(Ticket.Double);
			boolean revealFirst = isRevealRoundExt(currentRound);
			boolean revealSecond = isRevealRoundExt(currentRound + 1);
			TicketMove hiddenFirst = new TicketMove(Black, move.firstMove().ticket(), mrXLastKnownLocation);
			TicketMove hiddenSecond = new TicketMove(Black, move.secondMove().ticket(), mrXLastKnownLocation);
      players.get(0).location(move.finalDestination());
			if (revealFirst && revealSecond) {
				moveMade(move);
        mrXLastKnownLocation = move.finalDestination();
      }
      else if (revealFirst && !revealSecond) {
				for (Spectator spectator : spectators) {
						spectator.onMoveMade(view, new DoubleMove(Black, move.firstMove(), new TicketMove(Black,
							move.secondMove().ticket(), move.firstMove().destination())));
					}
				mrXLastKnownLocation = move.firstMove().destination();
      }
      else if (!revealFirst && revealSecond) {
				moveMade(new DoubleMove(Black, new TicketMove(Black, move.firstMove().ticket(),
						mrXLastKnownLocation), move.secondMove()));
				mrXLastKnownLocation = move.finalDestination();
      }
      else {
				moveMade(new DoubleMove(Black,hiddenFirst, hiddenSecond));
      }
			currentRound++;
			roundStart();
			players.get(0).removeTicket(move.firstMove().ticket());
			if (revealFirst) {
				moveMade(move.firstMove());
			}
			else {
				moveMade(hiddenFirst);
			}
			currentRound++;
			roundStart();
			players.get(0).removeTicket(move.secondMove().ticket());
			if (revealSecond) {
				moveMade(move.secondMove());
			}
			else {
				moveMade(new TicketMove(Black, move.secondMove().ticket(), mrXLastKnownLocation));
			}
  	}
	}
}
